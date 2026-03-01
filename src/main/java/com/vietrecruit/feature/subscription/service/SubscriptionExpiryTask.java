package com.vietrecruit.feature.subscription.service;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.feature.subscription.entity.SubscriptionStatus;
import com.vietrecruit.feature.subscription.repository.EmployerSubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled task that expires subscriptions past their expiry date. Runs daily at 02:00 AM
 * (Asia/Ho_Chi_Minh timezone).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryTask {

    private final EmployerSubscriptionRepository subscriptionRepository;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void expireSubscriptions() {
        var expired = subscriptionRepository.findExpiredActiveSubscriptions(Instant.now());

        if (expired.isEmpty()) {
            log.debug("No expired subscriptions found");
            return;
        }

        log.info("Expiring {} subscriptions", expired.size());

        for (var subscription : expired) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(subscription);
            log.info(
                    "Expired subscription id={} company={}",
                    subscription.getId(),
                    subscription.getCompanyId());
        }
    }
}
