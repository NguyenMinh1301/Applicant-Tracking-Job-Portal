package com.vietrecruit.feature.payment.service.impl;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.common.config.PayOSConfig;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.enums.BillingCycle;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.payment.dto.response.CheckoutResponse;
import com.vietrecruit.feature.payment.dto.response.PaymentStatusResponse;
import com.vietrecruit.feature.payment.entity.PaymentTransaction;
import com.vietrecruit.feature.payment.enums.PaymentStatus;
import com.vietrecruit.feature.payment.exception.WebhookVerificationException;
import com.vietrecruit.feature.payment.mapper.PaymentMapper;
import com.vietrecruit.feature.payment.repository.PaymentTransactionRepository;
import com.vietrecruit.feature.payment.repository.TransactionRecordRepository;
import com.vietrecruit.feature.payment.service.PaymentService;
import com.vietrecruit.feature.payment.service.WebhookSignatureVerifier;
import com.vietrecruit.feature.subscription.entity.SubscriptionPlan;
import com.vietrecruit.feature.subscription.enums.SubscriptionStatus;
import com.vietrecruit.feature.subscription.repository.EmployerSubscriptionRepository;
import com.vietrecruit.feature.subscription.repository.SubscriptionPlanRepository;
import com.vietrecruit.feature.subscription.service.SubscriptionService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.webhooks.WebhookData;

@Slf4j
@Service
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PayOS payOS;
    private final PayOSConfig payOSConfig;
    private final WebhookSignatureVerifier webhookSignatureVerifier;
    private final SubscriptionPlanRepository planRepository;
    private final EmployerSubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final TransactionRecordRepository transactionRecordRepository;
    private final SubscriptionService subscriptionService;
    private final SubscriptionActivationService subscriptionActivationService;
    private final PaymentMapper paymentMapper;
    private final Counter webhookSignatureFailureCounter;

    public PaymentServiceImpl(
            PayOS payOS,
            PayOSConfig payOSConfig,
            WebhookSignatureVerifier webhookSignatureVerifier,
            SubscriptionPlanRepository planRepository,
            EmployerSubscriptionRepository subscriptionRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            TransactionRecordRepository transactionRecordRepository,
            SubscriptionService subscriptionService,
            SubscriptionActivationService subscriptionActivationService,
            PaymentMapper paymentMapper,
            MeterRegistry meterRegistry) {
        this.payOS = payOS;
        this.payOSConfig = payOSConfig;
        this.webhookSignatureVerifier = webhookSignatureVerifier;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.transactionRecordRepository = transactionRecordRepository;
        this.subscriptionService = subscriptionService;
        this.subscriptionActivationService = subscriptionActivationService;
        this.paymentMapper = paymentMapper;
        this.webhookSignatureFailureCounter =
                Counter.builder("webhook.signature.failure")
                        .description("Number of webhook requests with invalid signatures")
                        .register(meterRegistry);
    }

    @Override
    @Transactional
    @Retry(name = "payosPayment", fallbackMethod = "checkoutFallback")
    @CircuitBreaker(name = "payosPayment", fallbackMethod = "checkoutFallback")
    public CheckoutResponse initiateCheckout(UUID companyId, UUID planId, BillingCycle cycle) {
        var plan =
                planRepository
                        .findById(planId)
                        .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PLAN_NOT_FOUND));

        // Block if company already has an active subscription — guard BEFORE circuit
        // breaker
        subscriptionRepository
                .findActiveByCompanyId(companyId, SubscriptionStatus.ACTIVE)
                .ifPresent(
                        existing -> {
                            log.warn(
                                    "Checkout rejected: company={} already has active subscription",
                                    companyId);
                            throw new ApiException(ApiErrorCode.SUBSCRIPTION_ALREADY_ACTIVE);
                        });

        // Resolve amount
        BigDecimal price =
                cycle == BillingCycle.YEARLY ? plan.getPriceYearly() : plan.getPriceMonthly();
        if (price == null) {
            price = plan.getPriceMonthly();
        }
        long amount = price.longValue();

        // Free plan: bypass PayOS, activate directly
        if (amount == 0) {
            subscriptionService.activateSubscription(companyId, plan, cycle);
            return CheckoutResponse.builder().checkoutUrl(null).orderCode(null).build();
        }

        // Cancel any existing pending payments for this company
        var pendingPayments =
                paymentTransactionRepository.findByCompanyIdAndStatus(
                        companyId, PaymentStatus.PENDING);
        for (var existing : pendingPayments) {
            existing.setStatus(PaymentStatus.CANCELLED);
            paymentTransactionRepository.save(existing);
            log.info(
                    "Cancelled existing pending payment orderCode={} for company={}",
                    existing.getOrderCode(),
                    companyId);
        }

        return createPaymentLinkWithRetry(companyId, plan, cycle, amount);
    }

    @Retry(name = "payosPayment", fallbackMethod = "checkoutFallback")
    @CircuitBreaker(name = "payosPayment", fallbackMethod = "checkoutFallback")
    private CheckoutResponse createPaymentLinkWithRetry(
            UUID companyId, SubscriptionPlan plan, BillingCycle cycle, long amount) {
        // Generate safe orderCode: epoch ms * 1000 + 3-digit random suffix
        // Max value: 9_999_999_999_999_999 < 9_007_199_254_740_991 (JS
        // MAX_SAFE_INTEGER)
        long orderCode = generateSafeOrderCode();

        // Build PayOS payment link request
        String description = "VietRecruit " + plan.getName() + " - " + cycle.name();
        // PayOS description max 25 chars
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        try {
            // Persist the transaction BEFORE calling PayOS.
            // If DB write fails the PayOS API is never called, preventing orphaned payment
            // links.
            // If PayOS call fails, @Transactional rolls back the DB record automatically.
            PaymentTransaction transaction =
                    PaymentTransaction.builder()
                            .orderCode(orderCode)
                            .companyId(companyId)
                            .plan(plan)
                            .billingCycle(cycle)
                            .amount(amount)
                            .status(PaymentStatus.PENDING)
                            .payosReference(String.valueOf(orderCode))
                            .build();

            paymentTransactionRepository.saveAndFlush(transaction);

            PaymentLinkItem item =
                    PaymentLinkItem.builder()
                            .name(plan.getName())
                            .quantity(1)
                            .price(amount)
                            .build();

            CreatePaymentLinkRequest paymentRequest =
                    CreatePaymentLinkRequest.builder()
                            .orderCode(orderCode)
                            .amount(amount)
                            .description(description)
                            .item(item)
                            .returnUrl(payOSConfig.getReturnUrl())
                            .cancelUrl(payOSConfig.getCancelUrl())
                            .build();

            CreatePaymentLinkResponse response = payOS.paymentRequests().create(paymentRequest);

            // Update the persisted record with the PayOS checkout URL
            transaction.setCheckoutUrl(response.getCheckoutUrl());

            return paymentMapper.toCheckoutResponse(transaction);

        } catch (DataIntegrityViolationException e) {
            // Partial unique index violation: another pending payment was created
            // concurrently
            log.warn(
                    "Concurrent checkout attempt for company={}, constraint violation: {}",
                    companyId,
                    e.getMessage());
            throw new ApiException(ApiErrorCode.PAYMENT_ALREADY_PENDING);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("PayOS payment link creation failed: {}", e.getMessage(), e);
            throw new ApiException(
                    ApiErrorCode.PAYMENT_CREATION_FAILED,
                    "Failed to create payment link: " + e.getMessage());
        }
    }

    /**
     * Generates a PayOS-safe orderCode within JavaScript MAX_SAFE_INTEGER range [1,
     * 9007199254740991].
     *
     * <p>Strategy: epoch milliseconds * 1000 + 3-digit random suffix. Max value: ~9.9e15, well
     * within JS safe integer range.
     *
     * @return unique orderCode guaranteed to be within PayOS constraints
     */
    private long generateSafeOrderCode() {
        long epochMs = System.currentTimeMillis();
        int suffix = SECURE_RANDOM.nextInt(100, 1000);
        long orderCode = epochMs * 1000L + suffix;

        // Verify collision-free (max 3 retries)
        for (int attempt = 0; attempt < 3; attempt++) {
            if (!paymentTransactionRepository.existsByOrderCode(orderCode)) {
                return orderCode;
            }
            log.warn("OrderCode collision detected: {}, retrying (attempt {})", orderCode, attempt);
            suffix = SECURE_RANDOM.nextInt(100, 1000);
            orderCode = epochMs * 1000L + suffix;
        }

        throw new ApiException(
                ApiErrorCode.INTERNAL_ERROR,
                "Failed to generate unique orderCode after 3 attempts");
    }

    /**
     * TX 1: Verify webhook, update payment status. Always commits independently of subscription
     * activation. This ensures PAID status is persisted even if activation fails.
     *
     * <p>Signature verification uses in-house HMAC-SHA256 with timing-safe comparison
     * (MessageDigest.isEqual) instead of the SDK's String.equals().
     */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public void handleWebhook(Object webhookBody) {
        // Deserialize payload to extract data and signature
        Map<String, Object> payload;
        try {
            payload = OBJECT_MAPPER.convertValue(webhookBody, Map.class);
        } catch (IllegalArgumentException e) {
            log.warn("Webhook payload deserialization failed: {}", e.getMessage());
            return;
        }

        Object rawData = payload.get("data");
        String signature = (String) payload.get("signature");

        // Verify signature (timing-safe)
        try {
            webhookSignatureVerifier.verify(rawData, signature);
        } catch (WebhookVerificationException e) {
            webhookSignatureFailureCounter.increment();
            log.warn("Webhook signature verification failed: {}", e.getMessage());
            throw new ApiException(ApiErrorCode.PAYMENT_WEBHOOK_INVALID_SIGNATURE);
        }

        // Signature valid — deserialize data into SDK model for business logic
        WebhookData data = OBJECT_MAPPER.convertValue(rawData, WebhookData.class);

        Long orderCode = data.getOrderCode();
        var txOpt = paymentTransactionRepository.findByOrderCode(orderCode);

        if (txOpt.isEmpty()) {
            log.warn("Webhook received for unknown orderCode={}", orderCode);
            return;
        }

        var tx = txOpt.get();

        // Idempotency: skip if already processed
        if (tx.getStatus() != PaymentStatus.PENDING) {
            log.info(
                    "Webhook for orderCode={} already processed (status={})",
                    orderCode,
                    tx.getStatus());
            return;
        }

        String code = data.getCode();

        if ("00".equals(code)) {
            // Payment confirmed — mark PAID in this TX
            tx.setStatus(PaymentStatus.PAID);
            tx.setPaidAt(Instant.now());
            paymentTransactionRepository.save(tx);

            // Persist transaction record for history (idempotent)
            if (!transactionRecordRepository.existsByOrderCode(orderCode)) {
                var record = paymentMapper.toTransactionRecord(data, tx.getCompanyId());
                transactionRecordRepository.save(record);
                log.info("Transaction record persisted for orderCode={}", orderCode);
            }

            log.info(
                    "Payment confirmed for orderCode={}, company={}, plan={}",
                    orderCode,
                    tx.getCompanyId(),
                    tx.getPlan().getCode());

            // TX 2: Activate subscription in a separate transaction (via injected bean)
            // If this fails, the recovery job will pick it up
            subscriptionActivationService.tryActivateSubscription(tx);
        } else {
            // Payment cancelled or failed
            tx.setStatus(PaymentStatus.CANCELLED);
            tx.setFailureCode(code);
            tx.setFailureReason(data.getDesc());
            paymentTransactionRepository.save(tx);

            log.info(
                    "Payment cancelled/failed for orderCode={}, code={}, reason={}",
                    orderCode,
                    code,
                    data.getDesc());
        }
    }

    @Override
    @Transactional
    public void activateAfterPayment(Long orderCode) {
        var tx =
                paymentTransactionRepository
                        .findByOrderCode(orderCode)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND));

        if (tx.getStatus() != PaymentStatus.PAID) {
            log.warn(
                    "activateAfterPayment called for orderCode={} but status={}",
                    orderCode,
                    tx.getStatus());
            return;
        }

        subscriptionActivationService.activateSubscription(tx);

        log.info(
                "Recovery: subscription activated for orderCode={}, company={}",
                orderCode,
                tx.getCompanyId());
    }

    @Override
    public PaymentStatusResponse getPaymentStatus(Long orderCode, UUID companyId) {
        var tx =
                paymentTransactionRepository
                        .findByOrderCode(orderCode)
                        .filter(t -> t.getCompanyId().equals(companyId))
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND));

        return paymentMapper.toPaymentStatusResponse(tx);
    }

    @SuppressWarnings("unused")
    private CheckoutResponse checkoutFallback(
            UUID companyId, UUID planId, BillingCycle cycle, Throwable t) {
        log.error("PayOS circuit breaker open: {}", t.getMessage());
        throw new ApiException(
                ApiErrorCode.PAYMENT_CREATION_FAILED,
                "Payment service is temporarily unavailable. Please try again later.");
    }
}
