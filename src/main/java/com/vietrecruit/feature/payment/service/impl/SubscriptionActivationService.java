package com.vietrecruit.feature.payment.service.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.payment.entity.PaymentTransaction;
import com.vietrecruit.feature.subscription.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracted from PaymentServiceImpl to break self-invocation AOP bypass. All subscription
 * activation calls go through this bean so REQUIRES_NEW propagation is honored by the Spring proxy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionActivationService {

    private final SubscriptionService subscriptionService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void tryActivateSubscription(PaymentTransaction tx) {
        try {
            subscriptionService.activateSubscription(
                    tx.getCompanyId(), tx.getPlan(), tx.getBillingCycle());
        } catch (DataIntegrityViolationException | ApiException | IllegalArgumentException e) {
            // Permanent errors: constraint violation, business rule violation, bad input.
            // No retry — log and move on.
            log.error(
                    "Subscription activation permanently failed for orderCode={}, company={}: {}",
                    tx.getOrderCode(),
                    tx.getCompanyId(),
                    e.getMessage());
        } catch (Exception e) {
            // Transient errors: DB timeout, connection failure, etc. Re-throw for retry.
            log.error(
                    "Subscription activation transiently failed for orderCode={}, company={}. "
                            + "Recovery job will retry. Error: {}",
                    tx.getOrderCode(),
                    tx.getCompanyId(),
                    e.getMessage(),
                    e);
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activateSubscription(PaymentTransaction tx) {
        subscriptionService.activateSubscription(
                tx.getCompanyId(), tx.getPlan(), tx.getBillingCycle());
    }
}
