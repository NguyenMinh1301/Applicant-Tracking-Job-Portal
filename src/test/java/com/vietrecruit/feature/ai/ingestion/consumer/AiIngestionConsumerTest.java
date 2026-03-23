package com.vietrecruit.feature.ai.ingestion.consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vietrecruit.feature.ai.shared.event.CvUploadedEvent;
import com.vietrecruit.feature.ai.shared.event.JobPublishedEvent;
import com.vietrecruit.feature.ai.shared.service.EmbeddingService;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.candidate.service.CandidateService;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.service.JobService;
import com.vietrecruit.feature.location.repository.LocationRepository;

/**
 * Unit tests for AI ingestion consumers: CvUploadedIngestionConsumer and
 * JobPublishedIngestionConsumer.
 */
@ExtendWith(MockitoExtension.class)
class AiIngestionConsumerTest {

    // ── CvUploadedIngestionConsumer dependencies ────────────────────────────

    @Mock private EmbeddingService embeddingService;
    @Mock private CandidateService candidateService;
    @Mock private CandidateRepository candidateRepository;
    @Mock private AiIngestionConsumer delegate;

    private CvUploadedIngestionConsumer cvConsumer;

    // ── JobPublishedIngestionConsumer dependencies ──────────────────────────

    @Mock private JobService jobService;
    @Mock private LocationRepository locationRepository;

    private JobPublishedIngestionConsumer jobConsumer;

    private UUID candidateId;
    private UUID jobId;
    private UUID employerId;

    @BeforeEach
    void setUp() {
        candidateId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        employerId = UUID.randomUUID();

        cvConsumer =
                new CvUploadedIngestionConsumer(
                        embeddingService, candidateService, candidateRepository, delegate);
        jobConsumer =
                new JobPublishedIngestionConsumer(embeddingService, jobService, locationRepository);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CvUploadedIngestionConsumer tests
    // ═══════════════════════════════════════════════════════════════════════

    // ── Scenario 1 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("cv-uploaded: valid payload with parsedCvText → embedAndStore called")
    void cvUploaded_validPayloadWithParsedText_embedAndStoreCalled() {
        CvUploadedEvent event =
                new CvUploadedEvent(candidateId, "cv/file.pdf", "candidate@example.com");

        Candidate candidate = mock(Candidate.class);
        when(candidate.getParsedCvText()).thenReturn("Java developer with 5 years experience");

        when(candidateService.findActiveCandidateById(candidateId))
                .thenReturn(Optional.of(candidate));

        cvConsumer.consume(event);

        verify(embeddingService)
                .embedAndStore(
                        eq("cv-" + candidateId),
                        eq("Java developer with 5 years experience"),
                        argThat(
                                m ->
                                        "cv".equals(m.get("type"))
                                                && candidateId
                                                        .toString()
                                                        .equals(m.get("candidateId"))));
    }

    // ── Scenario 2 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("cv-uploaded: candidate not found → embedAndStore not called, no exception")
    void cvUploaded_candidateNotFound_embedNotCalled() {
        // Using null candidateEmail to reflect the scenario label; when candidate is null,
        // the consumer returns early before accessing candidateEmail — no NPE.
        CvUploadedEvent event = new CvUploadedEvent(candidateId, "cv/file.pdf", null);

        when(candidateService.findActiveCandidateById(candidateId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> cvConsumer.consume(event));

        verifyNoInteractions(embeddingService);
    }

    @Test
    @DisplayName("cv-uploaded: candidate found but no CV text anywhere → embedAndStore not called")
    void cvUploaded_noCvTextAvailable_embedNotCalled() {
        CvUploadedEvent event =
                new CvUploadedEvent(candidateId, "cv/file.pdf", "candidate@example.com");

        Candidate candidate = mock(Candidate.class);
        when(candidate.getParsedCvText()).thenReturn(null);
        when(delegate.fetchAndParseCvFromR2("cv/file.pdf")).thenReturn(null);

        when(candidateService.findActiveCandidateById(candidateId))
                .thenReturn(Optional.of(candidate));

        assertDoesNotThrow(() -> cvConsumer.consume(event));

        verifyNoInteractions(embeddingService);
    }

    // ── Scenario 3 (job-published) ──────────────────────────────────────────

    @Test
    @DisplayName("job-published: valid payload → embedAndStore called with job content")
    void jobPublished_validPayload_embedAndStoreCalled() {
        JobPublishedEvent event = new JobPublishedEvent(jobId, employerId, "Senior Java Engineer");

        Job job = mock(Job.class);
        when(job.getTitle()).thenReturn("Senior Java Engineer");
        when(job.getDescription()).thenReturn("Build microservices");
        when(job.getRequirements()).thenReturn("5+ years Java");
        when(job.getMinSalary()).thenReturn(null);
        when(job.getMaxSalary()).thenReturn(null);
        when(job.getLocationId()).thenReturn(null);

        when(jobService.findJobById(jobId)).thenReturn(Optional.of(job));

        jobConsumer.consume(event);

        verify(embeddingService)
                .embedAndStore(
                        eq("job-" + jobId),
                        argThat(
                                text ->
                                        text.contains("Senior Java Engineer")
                                                && text.contains("Build microservices")
                                                && text.contains("5+ years Java")),
                        argThat(m -> "job".equals(m.get("type"))));
    }

    @Test
    @DisplayName("job-published: job not found → embedAndStore not called, no exception")
    void jobPublished_jobNotFound_embedNotCalled() {
        JobPublishedEvent event = new JobPublishedEvent(jobId, employerId, "Some Job");

        when(jobService.findJobById(jobId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> jobConsumer.consume(event));

        verifyNoInteractions(embeddingService);
    }

    // ── Scenario 4 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("EmbeddingService throws RuntimeException → propagates out of cv consumer")
    void cvUploaded_embeddingServiceThrows_exceptionPropagates() {
        CvUploadedEvent event =
                new CvUploadedEvent(candidateId, "cv/file.pdf", "candidate@example.com");

        Candidate candidate = mock(Candidate.class);
        when(candidate.getParsedCvText()).thenReturn("Some CV text");
        when(candidateService.findActiveCandidateById(candidateId))
                .thenReturn(Optional.of(candidate));
        doThrow(new RuntimeException("Embedding API unavailable"))
                .when(embeddingService)
                .embedAndStore(any(), any(), any());

        // RuntimeException propagates — Kafka's @RetryableTopic handles it
        assertThrows(RuntimeException.class, () -> cvConsumer.consume(event));
    }

    @Test
    @DisplayName("EmbeddingService throws RuntimeException → propagates out of job consumer")
    void jobPublished_embeddingServiceThrows_exceptionPropagates() {
        JobPublishedEvent event = new JobPublishedEvent(jobId, employerId, "Job Title");

        Job job = mock(Job.class);
        when(job.getTitle()).thenReturn("Job Title");
        when(job.getDescription()).thenReturn(null);
        when(job.getRequirements()).thenReturn(null);
        when(job.getMinSalary()).thenReturn(null);
        when(job.getMaxSalary()).thenReturn(null);
        when(job.getLocationId()).thenReturn(null);
        when(jobService.findJobById(jobId)).thenReturn(Optional.of(job));

        doThrow(new RuntimeException("Circuit open"))
                .when(embeddingService)
                .embedAndStore(any(), any(), any());

        assertThrows(RuntimeException.class, () -> jobConsumer.consume(event));
    }

    // ── Scenario 5: DLT handlers ────────────────────────────────────────────

    @Test
    @DisplayName("CV DLT handler invoked → completes without exception")
    void cvDltHandler_invoked_completesWithoutException() {
        CvUploadedEvent event =
                new CvUploadedEvent(candidateId, "cv/file.pdf", "candidate@example.com");
        ConsumerRecord<String, CvUploadedEvent> record =
                new ConsumerRecord<>("ai.cv-uploaded", 0, 0L, null, event);

        assertDoesNotThrow(
                () ->
                        cvConsumer.handleDlt(
                                record,
                                "java.lang.RuntimeException".getBytes(),
                                "retries exhausted".getBytes(),
                                "ai.cv-uploaded".getBytes()));
    }

    @Test
    @DisplayName("Job DLT handler invoked → completes without exception")
    void jobDltHandler_invoked_completesWithoutException() {
        JobPublishedEvent event = new JobPublishedEvent(jobId, employerId, "Job Title");
        ConsumerRecord<String, JobPublishedEvent> record =
                new ConsumerRecord<>("ai.job-published", 0, 0L, null, event);

        assertDoesNotThrow(
                () ->
                        jobConsumer.handleDlt(
                                record,
                                "java.lang.RuntimeException".getBytes(),
                                "retries exhausted".getBytes(),
                                "ai.job-published".getBytes()));
    }

    @Test
    @DisplayName("CV DLT handler with null event value → completes without NPE")
    void cvDltHandler_nullEventValue_completesWithoutNpe() {
        ConsumerRecord<String, CvUploadedEvent> record =
                new ConsumerRecord<>("ai.cv-uploaded.DLT", 0, 0L, null, null);

        assertDoesNotThrow(
                () ->
                        cvConsumer.handleDlt(
                                record,
                                "java.lang.RuntimeException".getBytes(),
                                "deserialization failed".getBytes(),
                                "ai.cv-uploaded.DLT".getBytes()));
    }
}
