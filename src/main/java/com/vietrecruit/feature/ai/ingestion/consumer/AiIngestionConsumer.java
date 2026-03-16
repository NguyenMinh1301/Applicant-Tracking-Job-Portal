package com.vietrecruit.feature.ai.ingestion.consumer;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.ai.shared.event.CvUploadedEvent;
import com.vietrecruit.feature.ai.shared.event.JobPublishedEvent;
import com.vietrecruit.feature.ai.shared.service.EmbeddingService;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.service.CandidateService;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.service.JobService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiIngestionConsumer {

    static final String TOPIC_CV_UPLOADED = "ai.cv-uploaded";
    static final String TOPIC_JOB_PUBLISHED = "ai.job-published";

    private final EmbeddingService embeddingService;
    private final CandidateService candidateService;
    private final JobService jobService;
    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = TOPIC_CV_UPLOADED, groupId = "ai-ingestion-cv-group")
    public void consumeCvUploaded(CvUploadedEvent event) {
        log.info("AI ingestion: CV uploaded event received: candidateId={}", event.candidateId());
        try {
            Candidate candidate =
                    candidateService.findActiveCandidateById(event.candidateId()).orElse(null);

            if (candidate == null) {
                log.warn(
                        "AI ingestion: candidate not found, skipping: candidateId={}",
                        event.candidateId());
                return;
            }

            String cvText = candidate.getParsedCvText();
            if (cvText == null || cvText.isBlank()) {
                cvText = fetchAndParseCvFromR2(event.cvFileKey());
            }

            if (cvText == null || cvText.isBlank()) {
                log.warn(
                        "AI ingestion: no CV text available for candidateId={}",
                        event.candidateId());
                return;
            }

            Map<String, Object> metadata =
                    Map.of(
                            "type", "cv",
                            "candidateId", event.candidateId().toString(),
                            "email", event.candidateEmail());

            embeddingService.embedAndStore("cv-" + event.candidateId(), cvText, metadata);

            log.info("AI ingestion: CV embedded successfully: candidateId={}", event.candidateId());
        } catch (Exception e) {
            log.error(
                    "AI ingestion: failed to process CV: candidateId={}, error={}",
                    event.candidateId(),
                    e.getMessage());
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = TOPIC_JOB_PUBLISHED, groupId = "ai-ingestion-job-group")
    public void consumeJobPublished(JobPublishedEvent event) {
        log.info("AI ingestion: Job published event received: jobId={}", event.jobId());
        try {
            Job job = jobService.findJobById(event.jobId()).orElse(null);

            if (job == null) {
                log.warn("AI ingestion: job not found, skipping: jobId={}", event.jobId());
                return;
            }

            StringBuilder textBuilder = new StringBuilder();
            textBuilder.append("Job Title: ").append(job.getTitle()).append("\n");
            if (job.getDescription() != null) {
                textBuilder.append("Description: ").append(job.getDescription()).append("\n");
            }
            if (job.getRequirements() != null) {
                textBuilder.append("Requirements: ").append(job.getRequirements()).append("\n");
            }

            Map<String, Object> metadata =
                    Map.of(
                            "type", "job",
                            "jobId", event.jobId().toString(),
                            "employerId", event.employerId().toString(),
                            "title", event.jobTitle());

            embeddingService.embedAndStore(
                    "job-" + event.jobId(), textBuilder.toString(), metadata);

            log.info("AI ingestion: Job embedded successfully: jobId={}", event.jobId());
        } catch (Exception e) {
            log.error(
                    "AI ingestion: failed to process job: jobId={}, error={}",
                    event.jobId(),
                    e.getMessage());
        }
    }

    @DltHandler
    public void handleCvDlt(ConsumerRecord<String, CvUploadedEvent> record, Exception ex) {
        log.error(
                "AI ingestion DLT: CV event exhausted retries: candidateId={}, topic={},"
                        + " partition={}, offset={}, error={}",
                record.value() != null ? record.value().candidateId() : "null",
                record.topic(),
                record.partition(),
                record.offset(),
                ex.getMessage());
    }

    @DltHandler
    public void handleJobDlt(ConsumerRecord<String, JobPublishedEvent> record, Exception ex) {
        log.error(
                "Job embedding DLT exhausted retries. topic={} partition={} offset={} jobId={}"
                        + " error={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.value() != null ? record.value().jobId() : "null",
                ex.getMessage());
    }

    private String fetchAndParseCvFromR2(String cvFileKey) {
        if (cvFileKey == null || cvFileKey.isBlank()) {
            return null;
        }
        try {
            GetObjectRequest request =
                    GetObjectRequest.builder().bucket(bucket).key(cvFileKey).build();
            var response = s3Client.getObject(request);

            org.springframework.ai.reader.tika.TikaDocumentReader reader =
                    new org.springframework.ai.reader.tika.TikaDocumentReader(
                            new InputStreamResource(response));
            var docs = reader.read();

            return docs.stream()
                    .map(org.springframework.ai.document.Document::getText)
                    .reduce("", (a, b) -> a + "\n" + b)
                    .trim();
        } catch (Exception e) {
            log.error(
                    "AI ingestion: failed to fetch/parse CV from R2: key={}, error={}",
                    cvFileKey,
                    e.getMessage());
            return null;
        }
    }
}
