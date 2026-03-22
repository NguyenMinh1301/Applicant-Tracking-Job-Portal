package com.vietrecruit.feature.ai.ingestion.consumer;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.ai.shared.event.CvUploadedEvent;
import com.vietrecruit.feature.ai.shared.service.EmbeddingService;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.service.CandidateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CvUploadedIngestionConsumer {

    static final String TOPIC_CV_UPLOADED = "ai.cv-uploaded";

    private final EmbeddingService embeddingService;
    private final CandidateService candidateService;
    private final AiIngestionConsumer delegate;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = TOPIC_CV_UPLOADED, groupId = "ai-ingestion-cv-group")
    public void consume(CvUploadedEvent event) {
        log.info("AI ingestion: CV uploaded event received: candidateId={}", event.candidateId());

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
            cvText = delegate.fetchAndParseCvFromR2(event.cvFileKey());
        }

        if (cvText == null || cvText.isBlank()) {
            log.warn("AI ingestion: no CV text available for candidateId={}", event.candidateId());
            return;
        }

        Map<String, Object> metadata =
                Map.of(
                        "type", "cv",
                        "candidateId", event.candidateId().toString(),
                        "email", event.candidateEmail());

        embeddingService.embedAndStore("cv-" + event.candidateId(), cvText, metadata);

        log.info("AI ingestion: CV embedded successfully: candidateId={}", event.candidateId());
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, CvUploadedEvent> record, Exception ex) {
        log.error(
                "AI ingestion DLT: CV event exhausted retries: candidateId={}, topic={},"
                        + " partition={}, offset={}, error={}",
                record.value() != null ? record.value().candidateId() : "null",
                record.topic(),
                record.partition(),
                record.offset(),
                ex.getMessage());
    }
}
