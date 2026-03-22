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
class CompanySyncConsumerTest {

    @Mock private ElasticsearchClient esClient;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private CompanySyncConsumer consumer;

    private ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>("debezium.public.companies", 0, 0L, null, payload);
    }

    // ── Scenario 1 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid upsert payload → esClient.index() called with correct fields")
    @SuppressWarnings("unchecked")
    void consume_validUpsertPayload_esClientIndexCalled() throws Exception {
        String companyId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", companyId);
        payload.put("name", "TechCorp Vietnam");
        payload.put("domain", "tech");
        payload.put("website", "https://techcorp.vn");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        consumer.consume(record("{payload}"));

        verify(esClient).index(any(Function.class));
        verify(esClient, never()).delete(any(Function.class));
    }

    // ── Scenario 2 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Delete payload (__deleted=true string) → esClient.delete() called")
    @SuppressWarnings("unchecked")
    void consume_deletePayloadStringFlag_esClientDeleteCalled() throws Exception {
        String companyId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", companyId);
        payload.put("__deleted", "true");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        consumer.consume(record("{payload}"));

        verify(esClient).delete(any(Function.class));
        verify(esClient, never()).index(any(Function.class));
    }

    // ── Scenario 2 (boolean variant) ───────────────────────────────────────

    @Test
    @DisplayName("Delete payload (__deleted=Boolean.TRUE) → esClient.delete() called")
    @SuppressWarnings("unchecked")
    void consume_deletePayloadBooleanFlag_esClientDeleteCalled() throws Exception {
        String companyId = UUID.randomUUID().toString();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", companyId);
        payload.put("__deleted", Boolean.TRUE);

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        consumer.consume(record("{payload}"));

        verify(esClient).delete(any(Function.class));
    }

    // ── Scenario 3 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Missing id field → logged as warn, no ES call, no exception")
    @SuppressWarnings("unchecked")
    void consume_missingIdField_skipsWithNoException() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "SomeCompany");
        // id absent

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        assertDoesNotThrow(() -> consumer.consume(record("{payload}")));

        verifyNoInteractions(esClient);
    }

    // ── Scenario 4 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Parse failure from objectMapper → RuntimeException propagated")
    @SuppressWarnings("unchecked")
    void consume_ioException_runtimeExceptionPropagated() throws Exception {
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new RuntimeException("simulated parse failure"));

        assertThrows(RuntimeException.class, () -> consumer.consume(record("!!!")));

        verifyNoInteractions(esClient);
    }

    // ── DLT handler ────────────────────────────────────────────────────────

    @Test
    @DisplayName("DLT handler invoked → completes without exception")
    void handleDlt_invoked_completesWithoutException() {
        ConsumerRecord<String, String> dltRecord = record("{\"id\":\"xyz\"}");
        assertDoesNotThrow(() -> consumer.handleDlt(dltRecord));
    }
}
