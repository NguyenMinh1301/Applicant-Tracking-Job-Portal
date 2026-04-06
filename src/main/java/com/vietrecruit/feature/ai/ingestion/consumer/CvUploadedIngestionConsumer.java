package com.vietrecruit.feature.ai.ingestion.consumer;

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

import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.ai.shared.event.CvUploadedEvent;
import com.vietrecruit.feature.ai.shared.service.EmbeddingService;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
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
    private final CandidateRepository candidateRepository;
    private final AiIngestionConsumer delegate;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = {ApiException.class})
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

        // Persist parsed CV text so downstream services (e.g. CvImprovementService) can read it
        if (candidate.getParsedCvText() == null || candidate.getParsedCvText().isBlank()) {
            candidate.setParsedCvText(cvText);
            candidateRepository.save(candidate);
            log.info(
                    "CV text extracted and stored for candidateId={}, chars={}",
                    event.candidateId(),
                    cvText.length());
        }

        Map<String, Object> metadata =
                Map.of("type", "cv", "candidateId", event.candidateId().toString());

        // Save and embed are intentionally non-atomic — @RetryableTopic covers embed failure on
        // retry
        embeddingService.embedAndStore("cv-" + event.candidateId(), cvText, metadata);

        log.info("AI ingestion: CV embedded successfully: candidateId={}", event.candidateId());
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, CvUploadedEvent> record,
            @Header(KafkaHeaders.DLT_EXCEPTION_FQCN) byte[] exceptionFqcn,
            @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) byte[] exceptionMessage,
            @Header(KafkaHeaders.DLT_ORIGINAL_TOPIC) byte[] originalTopic) {
        String candidateId =
                record.value() != null ? record.value().candidateId().toString() : "unknown";
        log.error(
                "CV ingestion permanently failed. candidateId={}, originalTopic={}, exception={},"
                        + " message={}",
                candidateId,
                new String(originalTopic),
                new String(exceptionFqcn),
                new String(exceptionMessage));
    }
}
