package com.vietrecruit.feature.payment.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vietrecruit.feature.payment.repository.PaymentTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recovery job that retries subscription activation for payments that were confirmed (PAID) but
 * whose subscription activation failed. Runs every 5 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentActivationRecoveryTask {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentService paymentService;

    @Scheduled(fixedRate = 300_000) // 5 minutes
    public void recoverFailedActivations() {
        var cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        var orphaned = paymentTransactionRepository.findPaidWithoutActiveSubscription(cutoff);

        if (orphaned.isEmpty()) {
            log.debug("No paid-without-subscription payments found");
            return;
        }

        log.info("Recovering {} paid payment(s) without active subscription", orphaned.size());

        for (var tx : orphaned) {
            try {
                paymentService.activateAfterPayment(tx.getOrderCode());
                log.info(
                        "Recovery succeeded for orderCode={}, company={}",
                        tx.getOrderCode(),
                        tx.getCompanyId());
            } catch (Exception e) {
                log.error(
                        "Recovery failed for orderCode={}, company={}. Will retry next cycle. Error: {}",
                        tx.getOrderCode(),
                        tx.getCompanyId(),
                        e.getMessage(),
                        e);
            }
        }
    }
}
