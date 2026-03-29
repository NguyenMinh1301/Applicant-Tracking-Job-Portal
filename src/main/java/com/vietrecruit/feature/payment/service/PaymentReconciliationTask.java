package com.vietrecruit.feature.payment.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.feature.payment.enums.PaymentStatus;
import com.vietrecruit.feature.payment.repository.PaymentTransactionRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

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
    private final PaymentService paymentService;
    private final PayOS payOS;

    @Scheduled(fixedRate = 600_000) // 10 minutes
    @Transactional
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
                var paymentLink = payOS.paymentRequests().get(tx.getOrderCode());
                PaymentLinkStatus status = paymentLink.getStatus();

                if (status == PaymentLinkStatus.PAID) {
                    tx.setStatus(PaymentStatus.PAID);
                    tx.setPaidAt(Instant.now());
                    paymentTransactionRepository.save(tx);
                    log.info(
                            "Reconciliation: orderCode={} confirmed PAID by PayOS",
                            tx.getOrderCode());

                    // Trigger activation
                    try {
                        paymentService.activateAfterPayment(tx.getOrderCode());
                    } catch (Exception e) {
                        log.error(
                                "Reconciliation: activation failed for orderCode={}, recovery job will handle",
                                tx.getOrderCode(),
                                e);
                    }
                } else if (status == PaymentLinkStatus.CANCELLED
                        || status == PaymentLinkStatus.EXPIRED) {
                    tx.setStatus(
                            status == PaymentLinkStatus.EXPIRED
                                    ? PaymentStatus.EXPIRED
                                    : PaymentStatus.CANCELLED);
                    paymentTransactionRepository.save(tx);
                    log.info(
                            "Reconciliation: orderCode={} is {} on PayOS",
                            tx.getOrderCode(),
                            status);
                } else {
                    log.debug(
                            "Reconciliation: orderCode={} still {} on PayOS, skipping",
                            tx.getOrderCode(),
                            status);
                }
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
