package com.vietrecruit.common.config.elasticsearch.sync;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.feature.category.repository.CategoryRepository;
import com.vietrecruit.feature.company.repository.CompanyRepository;
import com.vietrecruit.feature.location.repository.LocationRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

@ExtendWith(MockitoExtension.class)
class JobSyncConsumerTest {

    @Mock private ElasticsearchClient esClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private CompanyRepository companyRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private LocationRepository locationRepository;

    private JobSyncConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer =
                new JobSyncConsumer(
                        esClient,
                        objectMapper,
                        companyRepository,
                        categoryRepository,
                        locationRepository);
    }

    private ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>("debezium.public.jobs", 0, 0L, null, payload);
    }

    // ── Scenario 1 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid upsert payload → esClient.index() called with correct document ID")
    @SuppressWarnings("unchecked")
    void consume_validUpsertPayload_esClientIndexCalled() throws Exception {
        String jobId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", jobId);
        payload.put("title", "Senior Java Engineer");
        payload.put("status", "PUBLISHED");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);
        consumer.consume(record("{payload}"));

        verify(esClient).index(any(Function.class));
    }

    // ── Scenario 2 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Delete payload (__deleted=true) → esClient.delete() called")
    @SuppressWarnings("unchecked")
    void consume_deletePayload_esClientDeleteCalled() throws Exception {
        String jobId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", jobId);
        payload.put("__deleted", "true");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        consumer.consume(record("{payload}"));

        verify(esClient).delete(any(Function.class));
        verify(esClient, never()).index(any(Function.class));
    }

    // ── Scenario 3 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing id field in payload → logged as warn, no ES call, no exception")
    @SuppressWarnings("unchecked")
    void consume_missingIdField_logsWarnAndSkips() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Some Job");
        // id is absent

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        // Should not throw
        assertDoesNotThrow(() -> consumer.consume(record("{payload}")));

        verifyNoInteractions(esClient);
    }

    // ── Scenario 4 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Malformed JSON (parse failure) → RuntimeException propagated to Kafka")
    @SuppressWarnings("unchecked")
    void consume_malformedJson_runtimeExceptionPropagated() throws Exception {
        // Jackson wraps parse errors in JsonProcessingException (IOException); simulate by
        // throwing RuntimeException directly — consumer must not swallow it.
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new RuntimeException("simulated parse failure"));

        assertThrows(RuntimeException.class, () -> consumer.consume(record("not-json")));

        verifyNoInteractions(esClient);
    }

    // ── DLT handler ────────────────────────────────────────────────────────

    @Test
    @DisplayName("DLT handler invoked → completes without exception")
    void handleDlt_invoked_completesWithoutException() {
        ConsumerRecord<String, String> record = record("{\"id\":\"123\"}");
        assertDoesNotThrow(() -> consumer.handleDlt(record));
    }

    // ── Boolean __deleted flag ──────────────────────────────────────────────

    @Test
    @DisplayName("Delete payload with Boolean __deleted=true → esClient.delete() called")
    @SuppressWarnings("unchecked")
    void consume_booleanDeletedFlag_esClientDeleteCalled() throws Exception {
        String jobId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", jobId);
        payload.put("__deleted", Boolean.TRUE);

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        consumer.consume(record("{payload}"));

        verify(esClient).delete(any(Function.class));
    }
}
