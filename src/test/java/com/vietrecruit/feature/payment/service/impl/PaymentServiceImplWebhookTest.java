package com.vietrecruit.feature.payment.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vietrecruit.common.config.PayOSConfig;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.payment.entity.PaymentTransaction;
import com.vietrecruit.feature.payment.enums.PaymentStatus;
import com.vietrecruit.feature.payment.exception.WebhookVerificationException;
import com.vietrecruit.feature.payment.mapper.PaymentMapper;
import com.vietrecruit.feature.payment.repository.PaymentTransactionRepository;
import com.vietrecruit.feature.payment.repository.TransactionRecordRepository;
import com.vietrecruit.feature.payment.service.WebhookSignatureVerifier;
import com.vietrecruit.feature.subscription.entity.SubscriptionPlan;
import com.vietrecruit.feature.subscription.repository.EmployerSubscriptionRepository;
import com.vietrecruit.feature.subscription.repository.SubscriptionPlanRepository;
import com.vietrecruit.feature.subscription.service.SubscriptionService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import vn.payos.PayOS;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplWebhookTest {

    @Mock private PayOS payOS;
    @Mock private PayOSConfig payOSConfig;
    @Mock private WebhookSignatureVerifier webhookSignatureVerifier;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private EmployerSubscriptionRepository subscriptionRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private TransactionRecordRepository transactionRecordRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private PaymentMapper paymentMapper;

    private PaymentServiceImpl paymentService;

    private UUID companyId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();

        paymentService =
                new PaymentServiceImpl(
                        payOS,
                        payOSConfig,
                        webhookSignatureVerifier,
                        planRepository,
                        subscriptionRepository,
                        paymentTransactionRepository,
                        transactionRecordRepository,
                        subscriptionService,
                        paymentMapper,
                        new SimpleMeterRegistry());
    }

    @Test
    @DisplayName(
            "Valid signature, known orderCode — should update status to PAID and activate subscription")
    void handleWebhook_validSignature_paidSuccess() {
        Map<String, Object> data = webhookData("00", "Success", 123456L, 50000L);
        Map<String, Object> payload = webhookPayload(data, "valid-sig");

        // Verifier does not throw → signature is valid
        doNothing().when(webhookSignatureVerifier).verify(any(), eq("valid-sig"));

        PaymentTransaction tx = pendingTransaction(123456L);
        when(paymentTransactionRepository.findByOrderCode(123456L)).thenReturn(Optional.of(tx));
        when(transactionRecordRepository.existsByOrderCode(123456L)).thenReturn(false);
        when(paymentMapper.toTransactionRecord(any(), any())).thenReturn(null);

        paymentService.handleWebhook(payload);

        assertEquals(PaymentStatus.PAID, tx.getStatus());
        assertNotNull(tx.getPaidAt());
        verify(paymentTransactionRepository).save(tx);
    }

    @Test
    @DisplayName("Invalid signature — should not touch database")
    void handleWebhook_invalidSignature_noDatabaseAccess() {
        Map<String, Object> data = webhookData("00", "Success", 123456L, 50000L);
        Map<String, Object> payload = webhookPayload(data, "bad-sig");

        doThrow(new WebhookVerificationException("mismatch"))
                .when(webhookSignatureVerifier)
                .verify(any(), eq("bad-sig"));

        assertThrows(ApiException.class, () -> paymentService.handleWebhook(payload));

        verify(paymentTransactionRepository, never()).findByOrderCode(anyLong());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Valid signature, unknown orderCode — should log warning, no DB mutation")
    void handleWebhook_validSignature_unknownOrderCode() {
        Map<String, Object> data = webhookData("00", "Success", 999999L, 50000L);
        Map<String, Object> payload = webhookPayload(data, "valid-sig");

        doNothing().when(webhookSignatureVerifier).verify(any(), eq("valid-sig"));
        when(paymentTransactionRepository.findByOrderCode(999999L)).thenReturn(Optional.empty());

        paymentService.handleWebhook(payload);

        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Valid signature, already processed — idempotent, no duplicate activation")
    void handleWebhook_validSignature_alreadyProcessed() {
        Map<String, Object> data = webhookData("00", "Success", 123456L, 50000L);
        Map<String, Object> payload = webhookPayload(data, "valid-sig");

        doNothing().when(webhookSignatureVerifier).verify(any(), eq("valid-sig"));

        PaymentTransaction tx = pendingTransaction(123456L);
        tx.setStatus(PaymentStatus.PAID); // Already processed
        when(paymentTransactionRepository.findByOrderCode(123456L)).thenReturn(Optional.of(tx));

        paymentService.handleWebhook(payload);

        // Should not save again or activate subscription
        verify(paymentTransactionRepository, never()).save(any());
        verify(subscriptionService, never()).activateSubscription(any(), any(), any());
    }

    @Test
    @DisplayName("Malformed payload — should return silently without processing")
    void handleWebhook_malformedPayload_silentReturn() {
        // Pass a non-map object that cannot be deserialized
        paymentService.handleWebhook("not-a-json-object");

        verify(webhookSignatureVerifier, never()).verify(any(), any());
        verify(paymentTransactionRepository, never()).findByOrderCode(anyLong());
    }

    @Test
    @DisplayName("Valid signature, payment cancelled — should update status to CANCELLED")
    void handleWebhook_validSignature_paymentCancelled() {
        Map<String, Object> data = webhookData("01", "User cancelled", 123456L, 50000L);
        Map<String, Object> payload = webhookPayload(data, "valid-sig");

        doNothing().when(webhookSignatureVerifier).verify(any(), eq("valid-sig"));

        PaymentTransaction tx = pendingTransaction(123456L);
        when(paymentTransactionRepository.findByOrderCode(123456L)).thenReturn(Optional.of(tx));

        paymentService.handleWebhook(payload);

        assertEquals(PaymentStatus.CANCELLED, tx.getStatus());
        assertEquals("01", tx.getFailureCode());
        verify(paymentTransactionRepository).save(tx);
        verify(subscriptionService, never()).activateSubscription(any(), any(), any());
    }

    // --- Helpers ---

    private Map<String, Object> webhookData(String code, String desc, Long orderCode, Long amount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", orderCode);
        data.put("amount", amount);
        data.put("description", "Test");
        data.put("accountNumber", "123");
        data.put("reference", "ref");
        data.put("transactionDateTime", "2026-03-06");
        data.put("currency", "VND");
        data.put("paymentLinkId", "pl-1");
        data.put("code", code);
        data.put("desc", desc);
        data.put("counterAccountBankId", null);
        data.put("counterAccountBankName", null);
        data.put("counterAccountName", null);
        data.put("counterAccountNumber", null);
        data.put("virtualAccountName", null);
        data.put("virtualAccountNumber", null);
        return data;
    }

    private Map<String, Object> webhookPayload(Map<String, Object> data, String signature) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", "00");
        payload.put("desc", "success");
        payload.put("success", true);
        payload.put("data", data);
        payload.put("signature", signature);
        return payload;
    }

    private PaymentTransaction pendingTransaction(Long orderCode) {
        return PaymentTransaction.builder()
                .orderCode(orderCode)
                .companyId(companyId)
                .plan(
                        SubscriptionPlan.builder()
                                .id(UUID.randomUUID())
                                .code("BASIC")
                                .name("Basic")
                                .build())
                .status(PaymentStatus.PENDING)
                .amount(50000L)
                .build();
    }
}
