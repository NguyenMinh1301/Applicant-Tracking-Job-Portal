package com.vietrecruit.common.config.elasticsearch.sync;

import static com.vietrecruit.common.config.elasticsearch.ElasticsearchConstants.INDEX_COMPANIES;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.common.config.kafka.KafkaTopicNames;
import com.vietrecruit.feature.company.document.CompanyDocument;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanySyncConsumer {

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = "-es-dlq")
    @KafkaListener(
            topics = KafkaTopicNames.CDC_COMPANY,
            groupId = "es-sync-companies",
            containerFactory = "cdcKafkaListenerContainerFactory",
            concurrency = "2")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), MAP_TYPE);

            boolean isDeleted = isDeletedRecord(payload);
            String id = extractString(payload, "id");

            if (id == null) {
                log.warn("CDC company record missing id, skipping");
                return;
            }

            if (isDeleted) {
                deleteFromIndex(id);
                return;
            }

            upsertToIndex(id, payload);
        } catch (IOException e) {
            log.error("Failed to process CDC company record: {}", e.getMessage(), e);
            throw new RuntimeException("ES sync failed for company record", e);
        }
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        log.error(
                "Company ES sync DLT — key={}, topic={}, partition={}, offset={}",
                record.key(),
                record.topic(),
                record.partition(),
                record.offset());
    }

    private void upsertToIndex(String id, Map<String, Object> payload) throws IOException {
        CompanyDocument doc =
                CompanyDocument.builder()
                        .id(id)
                        .name(extractString(payload, "name"))
                        .domain(extractString(payload, "domain"))
                        .website(extractString(payload, "website"))
                        .createdAt(extractInstant(payload, "created_at"))
                        .updatedAt(extractInstant(payload, "updated_at"))
                        .build();

        esClient.index(i -> i.index(INDEX_COMPANIES).id(id).document(doc));
        log.debug("Indexed company [{}] to ES", id);
    }

    private void deleteFromIndex(String id) throws IOException {
        esClient.delete(d -> d.index(INDEX_COMPANIES).id(id));
        log.debug("Deleted company [{}] from ES", id);
    }

    private boolean isDeletedRecord(Map<String, Object> payload) {
        Object deleted = payload.get("__deleted");
        if (deleted instanceof Boolean b) return b;
        if (deleted instanceof String s) return "true".equalsIgnoreCase(s);
        return payload.get("deleted_at") != null;
    }

    private String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        if (val instanceof Number n) return Instant.ofEpochMilli(n.longValue());
        if (val instanceof String s) {
            try {
                return Instant.parse(s);
            } catch (Exception e) {
                try {
                    return Instant.ofEpochMilli(Long.parseLong(s));
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        return null;
    }
}
