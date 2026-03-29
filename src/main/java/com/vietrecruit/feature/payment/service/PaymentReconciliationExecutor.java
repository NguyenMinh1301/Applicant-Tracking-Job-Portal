package com.vietrecruit.feature.payment.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.feature.payment.entity.PaymentTransaction;
import com.vietrecruit.feature.payment.enums.PaymentStatus;
import com.vietrecruit.feature.payment.repository.PaymentTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;

/**
 * Extracted from PaymentReconciliationTask so each payment reconciliation runs in its own
 * REQUIRES_NEW transaction. Prevents a single failure from rolling back the entire batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationExecutor {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentService paymentService;
    private final PayOS payOS;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileSinglePayment(PaymentTransaction tx) throws Exception {
        var paymentLink = payOS.paymentRequests().get(tx.getOrderCode());
        PaymentLinkStatus status = paymentLink.getStatus();

        if (status == PaymentLinkStatus.PAID) {
            tx.setStatus(PaymentStatus.PAID);
            tx.setPaidAt(Instant.now());
            paymentTransactionRepository.save(tx);
            log.info(
                    "Reconciliation: orderCode={} confirmed PAID by PayOS", tx.getOrderCode());

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
    }
}
