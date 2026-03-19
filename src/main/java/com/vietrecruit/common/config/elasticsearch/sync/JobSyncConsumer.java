package com.vietrecruit.common.config.elasticsearch.sync;

import static com.vietrecruit.common.config.elasticsearch.ElasticsearchConstants.INDEX_JOBS;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.common.config.kafka.KafkaTopicNames;
import com.vietrecruit.feature.category.repository.CategoryRepository;
import com.vietrecruit.feature.company.repository.CompanyRepository;
import com.vietrecruit.feature.job.document.JobDocument;
import com.vietrecruit.feature.location.repository.LocationRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobSyncConsumer {

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;
    private final CompanyRepository companyRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            dltTopicSuffix = "-es-dlq")
    @KafkaListener(
            topics = KafkaTopicNames.CDC_JOB,
            groupId = "es-sync-jobs",
            containerFactory = "cdcKafkaListenerContainerFactory",
            concurrency = "3")
    public void consume(ConsumerRecord<String, String> record) {
        try {
            Map<String, Object> payload = objectMapper.readValue(record.value(), MAP_TYPE);

            boolean isDeleted = isDeletedRecord(payload);
            String id = extractString(payload, "id");

            if (id == null) {
                log.warn("CDC job record missing id, skipping");
                return;
            }

            if (isDeleted) {
                deleteFromIndex(id);
                return;
            }

            upsertToIndex(id, payload);
        } catch (IOException e) {
            log.error("Failed to process CDC job record: {}", e.getMessage(), e);
            throw new RuntimeException("ES sync failed for job record", e);
        }
    }

    @org.springframework.kafka.annotation.DltHandler
    public void handleDlt(ConsumerRecord<String, String> record) {
        log.error(
                "Job ES sync DLT — key={}, topic={}, partition={}, offset={}",
                record.key(),
                record.topic(),
                record.partition(),
                record.offset());
    }

    private void upsertToIndex(String id, Map<String, Object> payload) throws IOException {
        // Denormalize related entity names
        String companyName = resolveName(payload, "company_id", this::lookupCompanyName);
        String categoryName = resolveName(payload, "category_id", this::lookupCategoryName);
        String locationName = resolveName(payload, "location_id", this::lookupLocationName);

        JobDocument doc =
                JobDocument.builder()
                        .id(id)
                        .title(extractString(payload, "title"))
                        .description(extractString(payload, "description"))
                        .requirements(extractString(payload, "requirements"))
                        .status(extractString(payload, "status"))
                        .companyId(extractString(payload, "company_id"))
                        .companyName(companyName)
                        .categoryId(extractString(payload, "category_id"))
                        .categoryName(categoryName)
                        .locationId(extractString(payload, "location_id"))
                        .locationName(locationName)
                        .minSalary(extractDouble(payload, "min_salary"))
                        .maxSalary(extractDouble(payload, "max_salary"))
                        .currency(extractString(payload, "currency"))
                        .isNegotiable(extractBoolean(payload, "is_negotiable"))
                        .deadline(extractString(payload, "deadline"))
                        .publicLink(extractString(payload, "public_link"))
                        .createdAt(extractInstant(payload, "created_at"))
                        .updatedAt(extractInstant(payload, "updated_at"))
                        .build();

        esClient.index(i -> i.index(INDEX_JOBS).id(id).document(doc));
        log.debug("Indexed job [{}] to ES", id);
    }

    private void deleteFromIndex(String id) throws IOException {
        esClient.delete(d -> d.index(INDEX_JOBS).id(id));
        log.debug("Deleted job [{}] from ES", id);
    }

    private boolean isDeletedRecord(Map<String, Object> payload) {
        Object deleted = payload.get("__deleted");
        if (deleted instanceof Boolean b) return b;
        if (deleted instanceof String s) return "true".equalsIgnoreCase(s);
        return payload.get("deleted_at") != null;
    }

    private String resolveName(
            Map<String, Object> payload,
            String idField,
            java.util.function.Function<UUID, String> lookup) {
        String idStr = extractString(payload, idField);
        if (idStr == null || idStr.isBlank()) return null;
        try {
            return lookup.apply(UUID.fromString(idStr));
        } catch (Exception e) {
            log.debug("Could not resolve name for {}={}: {}", idField, idStr, e.getMessage());
            return null;
        }
    }

    private String lookupCompanyName(UUID id) {
        return companyRepository.findById(id).map(c -> c.getName()).orElse(null);
    }

    private String lookupCategoryName(UUID id) {
        return categoryRepository.findById(id).map(c -> c.getName()).orElse(null);
    }

    private String lookupLocationName(UUID id) {
        return locationRepository.findById(id).map(l -> l.getName()).orElse(null);
    }

    private String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private Double extractDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try {
                return Double.parseDouble(s);
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
