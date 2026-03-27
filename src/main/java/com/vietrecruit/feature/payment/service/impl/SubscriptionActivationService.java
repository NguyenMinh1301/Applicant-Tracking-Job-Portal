package com.vietrecruit.feature.payment.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        } catch (Exception e) {
            log.error(
                    "Subscription activation failed for orderCode={}, company={}. "
                            + "Recovery job will retry. Error: {}",
                    tx.getOrderCode(),
                    tx.getCompanyId(),
                    e.getMessage(),
                    e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activateSubscription(PaymentTransaction tx) {
        subscriptionService.activateSubscription(
                tx.getCompanyId(), tx.getPlan(), tx.getBillingCycle());
    }
}
