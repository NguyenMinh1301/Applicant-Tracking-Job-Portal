package com.vietrecruit.feature.subscription.service;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vietrecruit.feature.subscription.entity.EmployerSubscription;
import com.vietrecruit.feature.subscription.entity.SubscriptionStatus;
import com.vietrecruit.feature.subscription.repository.EmployerSubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryTaskTest {

    @Mock private EmployerSubscriptionRepository subscriptionRepository;
    @InjectMocks private SubscriptionExpiryTask expiryTask;

    @Test
    @DisplayName("Should expire subscriptions past their expiry date")
    void expireSubscriptions_MarksExpired() {
        var sub =
                EmployerSubscription.builder()
                        .id(UUID.randomUUID())
                        .companyId(UUID.randomUUID())
                        .status(SubscriptionStatus.ACTIVE)
                        .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                        .build();

        when(subscriptionRepository.findExpiredActiveSubscriptions(any(Instant.class)))
                .thenReturn(List.of(sub));

        expiryTask.expireSubscriptions();

        verify(subscriptionRepository).save(sub);
        assert sub.getStatus() == SubscriptionStatus.EXPIRED;
    }

    @Test
    @DisplayName("Should do nothing when no expired subscriptions")
    void expireSubscriptions_NoneExpired() {
        when(subscriptionRepository.findExpiredActiveSubscriptions(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        expiryTask.expireSubscriptions();

        verify(subscriptionRepository, never()).save(any());
    }
}
