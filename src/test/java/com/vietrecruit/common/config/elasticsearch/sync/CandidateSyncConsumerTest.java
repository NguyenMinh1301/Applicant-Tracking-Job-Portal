package com.vietrecruit.common.config.elasticsearch.sync;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

@ExtendWith(MockitoExtension.class)
class CandidateSyncConsumerTest {

    @Mock private ElasticsearchClient esClient;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private CandidateSyncConsumer consumer;

    private ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>("debezium.public.candidates", 0, 0L, null, payload);
    }

    // ── Scenario 1 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid upsert payload → esClient.index() called")
    @SuppressWarnings("unchecked")
    void consume_validUpsertPayload_esClientIndexCalled() throws Exception {
        String candidateId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", candidateId);
        payload.put("headline", "Backend Developer");
        payload.put("is_open_to_work", true);

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        consumer.consume(record("{payload}"));

        verify(esClient).index(any(Function.class));
    }

    // ── Scenario 2 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Delete payload (__deleted=true) → esClient.delete() called, index not called")
    @SuppressWarnings("unchecked")
    void consume_deletePayload_esClientDeleteCalled() throws Exception {
        String candidateId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", candidateId);
        payload.put("__deleted", "true");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        consumer.consume(record("{payload}"));

        verify(esClient).delete(any(Function.class));
        verify(esClient, never()).index(any(Function.class));
    }

    // ── Scenario 3 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing id field → logged as warn, no ES call, no exception propagated")
    @SuppressWarnings("unchecked")
    void consume_missingIdField_logsWarnAndSkips() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("headline", "Java Developer");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        assertDoesNotThrow(() -> consumer.consume(record("{payload}")));

        verifyNoInteractions(esClient);
    }

    // ── Scenario 4 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Parse failure from objectMapper → RuntimeException propagated to Kafka for retry")
    @SuppressWarnings("unchecked")
    void consume_ioException_runtimeExceptionPropagated() throws Exception {
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new RuntimeException("simulated parse error"));

        assertThrows(RuntimeException.class, () -> consumer.consume(record("bad-json")));

        verifyNoInteractions(esClient);
    }

    // ── DLT handler ────────────────────────────────────────────────────────

    @Test
    @DisplayName("DLT handler invoked → completes without exception")
    void handleDlt_invoked_completesWithoutException() {
        ConsumerRecord<String, String> dltRecord = record("{\"id\":\"abc\"}");
        assertDoesNotThrow(() -> consumer.handleDlt(dltRecord));
    }

    // ── Skills as PostgreSQL array string ───────────────────────────────────

    @Test
    @DisplayName("Skills field as PostgreSQL array string → parsed correctly, index called")
    @SuppressWarnings("unchecked")
    void consume_skillsAsPostgresArrayString_indexCalled() throws Exception {
        String candidateId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", candidateId);
        payload.put("skills", "{java,spring,docker}");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        assertDoesNotThrow(() -> consumer.consume(record("{payload}")));

        verify(esClient).index(any(Function.class));
    }
}
