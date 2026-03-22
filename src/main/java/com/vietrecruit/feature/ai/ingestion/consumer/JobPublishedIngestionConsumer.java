package com.vietrecruit.feature.ai.ingestion.consumer;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.ai.shared.event.JobPublishedEvent;
import com.vietrecruit.feature.ai.shared.service.EmbeddingService;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.service.JobService;
import com.vietrecruit.feature.location.repository.LocationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobPublishedIngestionConsumer {

    static final String TOPIC_JOB_PUBLISHED = "ai.job-published";

    private final EmbeddingService embeddingService;
    private final JobService jobService;
    private final LocationRepository locationRepository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = TOPIC_JOB_PUBLISHED, groupId = "ai-ingestion-job-group")
    public void consume(JobPublishedEvent event) {
        log.info("AI ingestion: Job published event received: jobId={}", event.jobId());

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

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "job");
        metadata.put("jobId", event.jobId().toString());
        metadata.put("employerId", event.employerId().toString());
        metadata.put("title", event.jobTitle());
        metadata.put("hasSalary", job.getMinSalary() != null ? "true" : "false");
        if (job.getMinSalary() != null) {
            metadata.put("salaryMin", job.getMinSalary().toPlainString());
        }
        if (job.getMaxSalary() != null) {
            metadata.put("salaryMax", job.getMaxSalary().toPlainString());
        }
        if (job.getLocationId() != null) {
            locationRepository
                    .findById(job.getLocationId())
                    .ifPresent(loc -> metadata.put("locationName", loc.getName()));
        }

        embeddingService.embedAndStore(event.jobId().toString(), textBuilder.toString(), metadata);

        log.info("AI ingestion: Job embedded successfully: jobId={}", event.jobId());
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, JobPublishedEvent> record,
            @Header(KafkaHeaders.DLT_EXCEPTION_FQCN) byte[] exceptionFqcn,
            @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) byte[] exceptionMessage,
            @Header(KafkaHeaders.DLT_ORIGINAL_TOPIC) byte[] originalTopic) {
        String jobId = record.value() != null ? record.value().jobId().toString() : "unknown";
        log.error(
                "Job ingestion permanently failed. jobId={}, originalTopic={}, exception={},"
                        + " message={}",
                jobId,
                new String(originalTopic),
                new String(exceptionFqcn),
                new String(exceptionMessage));
    }
}
