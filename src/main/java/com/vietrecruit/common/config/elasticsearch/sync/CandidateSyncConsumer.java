package com.vietrecruit.common.config.elasticsearch.sync;

import static com.vietrecruit.common.config.elasticsearch.ElasticsearchConstants.INDEX_CANDIDATES;

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
import com.vietrecruit.feature.candidate.document.CandidateDocument;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateSyncConsumer {

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
            topics = KafkaTopicNames.CDC_CANDIDATE,
            groupId = "es-sync-candidates",
            containerFactory = "cdcKafkaListenerContainerFactory",
            concurrency = "3")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), MAP_TYPE);

            boolean isDeleted = isDeletedRecord(payload);
            String id = extractString(payload, "id");

            if (id == null) {
                log.warn("CDC candidate record missing id, skipping");
                return;
            }

            if (isDeleted) {
                deleteFromIndex(id);
                return;
            }

            upsertToIndex(id, payload);
        } catch (IOException e) {
            log.error("Failed to process CDC candidate record: {}", e.getMessage(), e);
            throw new RuntimeException("ES sync failed for candidate record", e);
        }
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        log.error(
                "Candidate ES sync DLT — key={}, topic={}, partition={}, offset={}",
                record.key(),
                record.topic(),
                record.partition(),
                record.offset());
    }

    private void upsertToIndex(String id, Map<String, Object> payload) throws IOException {
        String[] skills = parseSkills(payload.get("skills"));

        CandidateDocument doc =
                CandidateDocument.builder()
                        .id(id)
                        .userId(extractString(payload, "user_id"))
                        .headline(extractString(payload, "headline"))
                        .summary(extractString(payload, "summary"))
                        .desiredPosition(extractString(payload, "desired_position"))
                        .desiredPositionLevel(extractString(payload, "desired_position_level"))
                        .yearsOfExperience(extractShort(payload, "years_of_experience"))
                        .skills(skills)
                        .workType(extractString(payload, "work_type"))
                        .desiredSalaryMin(extractLong(payload, "desired_salary_min"))
                        .desiredSalaryMax(extractLong(payload, "desired_salary_max"))
                        .educationLevel(extractString(payload, "education_level"))
                        .educationMajor(extractString(payload, "education_major"))
                        .isOpenToWork(extractBoolean(payload, "is_open_to_work"))
                        .availableFrom(extractString(payload, "available_from"))
                        .createdAt(extractInstant(payload, "created_at"))
                        .updatedAt(extractInstant(payload, "updated_at"))
                        .build();

        esClient.index(i -> i.index(INDEX_CANDIDATES).id(id).document(doc));
        log.debug("Indexed candidate [{}] to ES", id);
    }

    private void deleteFromIndex(String id) throws IOException {
        esClient.delete(d -> d.index(INDEX_CANDIDATES).id(id));
        log.debug("Deleted candidate [{}] from ES", id);
    }

    private boolean isDeletedRecord(Map<String, Object> payload) {
        Object deleted = payload.get("__deleted");
        if (deleted instanceof Boolean b) return b;
        if (deleted instanceof String s) return "true".equalsIgnoreCase(s);
        return payload.get("deleted_at") != null;
    }

    @SuppressWarnings("unchecked")
    private String[] parseSkills(Object raw) {
        if (raw == null) return null;
        if (raw instanceof String[] arr) return arr;
        if (raw instanceof java.util.List<?> list) {
            return list.stream().map(Object::toString).toArray(String[]::new);
        }
        // PostgreSQL array from Debezium comes as comma-separated string like {java,spring,react}
        if (raw instanceof String s) {
            String cleaned = s.replaceAll("[{}]", "");
            if (cleaned.isEmpty()) return new String[0];
            return cleaned.split(",");
        }
        return null;
    }

    private String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private Short extractShort(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.shortValue();
        if (val instanceof String s) {
            try {
                return Short.parseShort(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Long extractLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Boolean extractBoolean(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return null;
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
