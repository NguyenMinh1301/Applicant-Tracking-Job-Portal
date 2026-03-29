package com.vietrecruit.feature.payment.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.payment.enums.PaymentStatus;
import com.vietrecruit.feature.payment.repository.PaymentTransactionRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reconciliation job that polls PayOS API for stale PENDING transactions. Detects cases where
 * webhooks were never delivered. Runs every 10 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationTask {

    private static final int STALE_MINUTES = 5;
    private static final int MAX_AGE_MINUTES = 30;

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentReconciliationExecutor reconciliationExecutor;

    @Scheduled(fixedRate = 600_000) // 10 minutes
    @Retry(name = "payosPayment", fallbackMethod = "reconciliationFallback")
    @CircuitBreaker(name = "payosPayment", fallbackMethod = "reconciliationFallback")
    public void reconcilePendingPayments() {
        var cutoff = Instant.now().minus(STALE_MINUTES, ChronoUnit.MINUTES);
        var lowerBound = Instant.now().minus(MAX_AGE_MINUTES, ChronoUnit.MINUTES);
        var stale =
                paymentTransactionRepository.findStalePending(
                        cutoff, lowerBound, PaymentStatus.PENDING);

        if (stale.isEmpty()) {
            log.debug("No stale pending payments found for reconciliation");
            return;
        }

        log.info("Reconciling {} stale pending payment(s)", stale.size());

        for (var tx : stale) {
            try {
                reconciliationExecutor.reconcileSinglePayment(tx);
            } catch (Exception e) {
                log.error(
                        "Reconciliation: failed to check orderCode={}: {}",
                        tx.getOrderCode(),
                        e.getMessage(),
                        e);
            }
        }
    }

    @SuppressWarnings("unused")
    private void reconciliationFallback(Throwable t) {
        log.warn(
                "PayOS reconciliation circuit breaker open, skipping this cycle: {}",
                t.getMessage());
    }
}
