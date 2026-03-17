package com.vietrecruit.feature.ai.knowledge.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.ai.knowledge.dto.KnowledgeUploadedEvent;
import com.vietrecruit.feature.ai.knowledge.repository.KnowledgeDocumentRepository;
import com.vietrecruit.feature.ai.knowledge.service.KnowledgeIngestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIngestionConsumer {

    static final String TOPIC_KNOWLEDGE_UPLOADED = "ai.knowledge-uploaded";

    private final KnowledgeIngestionService knowledgeIngestionService;
    private final KnowledgeDocumentRepository repository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR)
    @KafkaListener(topics = TOPIC_KNOWLEDGE_UPLOADED, groupId = "ai-ingestion-knowledge-group")
    public void consume(KnowledgeUploadedEvent event) {
        log.info(
                "Knowledge ingestion: event received: documentId={}, category={}",
                event.documentId(),
                event.category());

        knowledgeIngestionService.processIngestion(
                event.documentId(), event.fileKey(), event.category(), event.title());
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, KnowledgeUploadedEvent> record, Exception ex) {
        log.error(
                "Knowledge ingestion DLT: exhausted retries: documentId={}, topic={}, partition={},"
                        + " offset={}, error={}",
                record.value() != null ? record.value().documentId() : "null",
                record.topic(),
                record.partition(),
                record.offset(),
                ex.getMessage());

        if (record.value() != null) {
            repository
                    .findById(record.value().documentId())
                    .ifPresent(
                            doc -> {
                                doc.setStatus("FAILED");
                                repository.save(doc);
                            });
        }
    }
}
