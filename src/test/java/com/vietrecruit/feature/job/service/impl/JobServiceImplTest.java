package com.vietrecruit.feature.job.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

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
import com.vietrecruit.feature.job.dto.request.JobCreateRequest;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.enums.JobStatus;
import com.vietrecruit.feature.job.mapper.JobMapper;
import com.vietrecruit.feature.job.repository.JobRepository;
import com.vietrecruit.feature.subscription.service.QuotaGuard;

@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobMapper jobMapper;
    @Mock private QuotaGuard quotaGuard;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private CacheEventPublisher cacheEventPublisher;
    @InjectMocks private JobServiceImpl jobService;

    private UUID companyId;
    private UUID userId;
    private UUID jobId;
    private Job draftJob;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        draftJob =
                Job.builder()
                        .id(jobId)
                        .companyId(companyId)
                        .title("Senior Engineer")
                        .description("Build things")
                        .status(JobStatus.DRAFT)
                        .build();
    }

    @Test
    @DisplayName("Should create job in DRAFT status")
    void createJob_Draft() {
        var request = JobCreateRequest.builder().title("Title").description("Desc").build();
        when(jobMapper.toEntity(request))
                .thenReturn(Job.builder().title("Title").description("Desc").build());
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        var job = jobService.createJob(companyId, userId, request);

        assertEquals(JobStatus.DRAFT, job.getStatus());
        assertEquals(companyId, job.getCompanyId());
        verifyNoInteractions(quotaGuard);
    }

    @Test
    @DisplayName("Should publish DRAFT job with quota validation")
    void publishJob_Success() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(draftJob));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        var published = jobService.publishJob(companyId, jobId);

        assertEquals(JobStatus.PUBLISHED, published.getStatus());
        verify(quotaGuard).validateAndIncrementActiveJobs(companyId);
    }

    @Test
    @DisplayName("Should reject publishing non-DRAFT job")
    void publishJob_NotDraft() {
        draftJob.setStatus(JobStatus.PUBLISHED);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(draftJob));

        var ex = assertThrows(ApiException.class, () -> jobService.publishJob(companyId, jobId));
        assertEquals(ApiErrorCode.BAD_REQUEST, ex.getErrorCode());
        verifyNoInteractions(quotaGuard);
    }

    @Test
    @DisplayName("Should close PUBLISHED job and decrement quota")
    void closeJob_Success() {
        draftJob.setStatus(JobStatus.PUBLISHED);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(draftJob));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        var closed = jobService.closeJob(companyId, jobId);

        assertEquals(JobStatus.CLOSED, closed.getStatus());
        verify(quotaGuard).decrementActiveJobs(companyId);
    }

    @Test
    @DisplayName("Should reject closing non-PUBLISHED job")
    void closeJob_NotPublished() {
        draftJob.setStatus(JobStatus.DRAFT);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(draftJob));

        var ex = assertThrows(ApiException.class, () -> jobService.closeJob(companyId, jobId));
        assertEquals(ApiErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should throw NOT_FOUND for wrong company")
    void publishJob_WrongCompany() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(draftJob));

        var otherCompany = UUID.randomUUID();
        var ex = assertThrows(ApiException.class, () -> jobService.publishJob(otherCompany, jobId));
        assertEquals(ApiErrorCode.NOT_FOUND, ex.getErrorCode());
    }
}
