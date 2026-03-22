package com.vietrecruit.feature.job.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.vietrecruit.common.config.cache.CacheEventPublisher;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.enums.JobStatus;
import com.vietrecruit.feature.job.mapper.JobMapper;
import com.vietrecruit.feature.job.repository.JobRepository;
import com.vietrecruit.feature.subscription.service.QuotaGuard;

/**
 * Concurrency tests for job publish with quota enforcement.
 *
 * <p>These tests verify service-level behaviour under concurrent load by simulating a quota-limited
 * QuotaGuard using a thread-safe counter. For full DB-level concurrency guarantees (row-level
 * locking, optimistic version checks), an integration test backed by Testcontainers PostgreSQL
 * would be required; that test would use @SpringBootTest + @Testcontainers with real repositories
 * and the same concurrent submission approach shown here.
 */
@ExtendWith(MockitoExtension.class)
class JobServiceImplConcurrencyTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobMapper jobMapper;
    @Mock private QuotaGuard quotaGuard;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private CacheEventPublisher cacheEventPublisher;

    @InjectMocks private JobServiceImpl jobService;

    private static final int THREAD_COUNT = 10;

    private UUID companyId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
    }

    // ── Scenario: quota = 1, 10 concurrent publish attempts ────────────────

    @Test
    @DisplayName(
            "Quota=1, 10 concurrent publish requests → exactly 1 succeeds, 9 get QUOTA_EXCEEDED")
    void publishJob_quotaOne_10ConcurrentRequests_exactlyOneSucceeds() throws Exception {
        // Each thread publishes its own DRAFT job
        int n = THREAD_COUNT;
        List<UUID> jobIds = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID jid = UUID.randomUUID();
            jobIds.add(jid);
            Job draft =
                    Job.builder()
                            .id(jid)
                            .companyId(companyId)
                            .status(JobStatus.DRAFT)
                            .title("Job " + i)
                            .build();
            lenient().when(jobRepository.findById(jid)).thenReturn(Optional.of(draft));
        }
        lenient().when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        // QuotaGuard enforces: first call passes, subsequent calls fail
        AtomicInteger publishCount = new AtomicInteger(0);
        doAnswer(
                        inv -> {
                            int current = publishCount.getAndIncrement();
                            if (current >= 1) {
                                throw new ApiException(ApiErrorCode.QUOTA_EXCEEDED);
                            }
                            return null;
                        })
                .when(quotaGuard)
                .validateCanPublishJob(companyId);

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(n);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            final UUID jobId = jobIds.get(i);
            futures.add(
                    executor.submit(
                            () -> {
                                try {
                                    startGate.await();
                                    jobService.publishJob(companyId, jobId);
                                } catch (ApiException e) {
                                    // expected for quota-exceeded threads
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }));
        }

        startGate.countDown(); // release all threads simultaneously

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Exactly 1 job was published (quota guard allowed exactly 1)
        verify(quotaGuard, times(1)).incrementActiveJobs(companyId);
        // 10 calls to validateCanPublishJob, 9 threw QUOTA_EXCEEDED
        verify(quotaGuard, times(n)).validateCanPublishJob(companyId);
    }

    // ── Scenario: quota = 0 from the start ─────────────────────────────────

    @Test
    @DisplayName("Quota=0, any publish request → QUOTA_EXCEEDED immediately")
    void publishJob_quotaZero_throwsQuotaExceeded() {
        UUID jobId = UUID.randomUUID();
        Job draft = Job.builder().id(jobId).companyId(companyId).status(JobStatus.DRAFT).build();

        when(jobRepository.findById(jobId)).thenReturn(Optional.of(draft));
        doThrow(new ApiException(ApiErrorCode.QUOTA_EXCEEDED))
                .when(quotaGuard)
                .validateCanPublishJob(companyId);

        ApiException ex =
                assertThrows(ApiException.class, () -> jobService.publishJob(companyId, jobId));

        assertEquals(ApiErrorCode.QUOTA_EXCEEDED, ex.getErrorCode());
        verify(quotaGuard, never()).incrementActiveJobs(any());
        verify(jobRepository, never()).save(any());
    }
}
