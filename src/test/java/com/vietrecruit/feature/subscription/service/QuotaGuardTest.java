package com.vietrecruit.feature.subscription.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.subscription.entity.EmployerSubscription;
import com.vietrecruit.feature.subscription.entity.JobPostingQuota;
import com.vietrecruit.feature.subscription.entity.SubscriptionPlan;
import com.vietrecruit.feature.subscription.enums.SubscriptionStatus;
import com.vietrecruit.feature.subscription.repository.EmployerSubscriptionRepository;
import com.vietrecruit.feature.subscription.repository.JobPostingQuotaRepository;

@ExtendWith(MockitoExtension.class)
class QuotaGuardTest {

    @Mock private EmployerSubscriptionRepository subscriptionRepository;
    @Mock private JobPostingQuotaRepository quotaRepository;
    @InjectMocks private QuotaGuard quotaGuard;

    private UUID companyId;
    private SubscriptionPlan plan;
    private EmployerSubscription subscription;
    private JobPostingQuota quota;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();

        plan =
                SubscriptionPlan.builder()
                        .id(UUID.randomUUID())
                        .code("BASIC")
                        .name("Basic")
                        .maxActiveJobs(5)
                        .build();

        subscription =
                EmployerSubscription.builder()
                        .id(UUID.randomUUID())
                        .companyId(companyId)
                        .plan(plan)
                        .status(SubscriptionStatus.ACTIVE)
                        .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                        .build();

        quota =
                JobPostingQuota.builder()
                        .id(UUID.randomUUID())
                        .subscription(subscription)
                        .jobsActive(2)
                        .jobsPosted(10)
                        .build();
    }

    @Test
    @DisplayName(
            "Should atomically validate and increment with valid subscription and remaining quota")
    void validateAndIncrementActiveJobs_Success() {
        when(subscriptionRepository.findActiveByCompanyId(companyId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        // atomicIncrementIfUnderLimit returns 1 → success
        when(quotaRepository.atomicIncrementIfUnderLimit(
                        subscription.getId(), plan.getMaxActiveJobs()))
                .thenReturn(1);

        assertDoesNotThrow(() -> quotaGuard.validateAndIncrementActiveJobs(companyId));
    }

    @Test
    @DisplayName("Should throw SUBSCRIPTION_REQUIRED when no active subscription")
    void validateAndIncrementActiveJobs_NoSubscription() {
        when(subscriptionRepository.findActiveByCompanyId(companyId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        var ex =
                assertThrows(
                        ApiException.class,
                        () -> quotaGuard.validateAndIncrementActiveJobs(companyId));
        assertEquals(ApiErrorCode.SUBSCRIPTION_REQUIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should throw SUBSCRIPTION_EXPIRED when subscription is past expiry")
    void validateAndIncrementActiveJobs_Expired() {
        subscription.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(subscriptionRepository.findActiveByCompanyId(companyId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));

        var ex =
                assertThrows(
                        ApiException.class,
                        () -> quotaGuard.validateAndIncrementActiveJobs(companyId));
        assertEquals(ApiErrorCode.SUBSCRIPTION_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should throw QUOTA_EXCEEDED when atomicIncrementIfUnderLimit returns 0")
    void validateAndIncrementActiveJobs_QuotaExceeded() {
        when(subscriptionRepository.findActiveByCompanyId(companyId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        // 0 rows updated → limit already reached
        when(quotaRepository.atomicIncrementIfUnderLimit(
                        subscription.getId(), plan.getMaxActiveJobs()))
                .thenReturn(0);

        var ex =
                assertThrows(
                        ApiException.class,
                        () -> quotaGuard.validateAndIncrementActiveJobs(companyId));
        assertEquals(ApiErrorCode.QUOTA_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName(
            "Unlimited plan (-1) — atomicIncrementIfUnderLimit is still called with -1 limit and must succeed")
    void validateAndIncrementActiveJobs_UnlimitedPlan() {
        plan.setMaxActiveJobs(-1);
        when(subscriptionRepository.findActiveByCompanyId(companyId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(quotaRepository.atomicIncrementIfUnderLimit(subscription.getId(), -1)).thenReturn(1);

        assertDoesNotThrow(() -> quotaGuard.validateAndIncrementActiveJobs(companyId));
    }

    @Test
    @DisplayName("Atomic validate+increment completes without error when DB reports 1 row updated")
    void validateAndIncrementActiveJobs_DbReturnsOneRow_success() {
        when(subscriptionRepository.findActiveByCompanyId(companyId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(quotaRepository.atomicIncrementIfUnderLimit(
                        subscription.getId(), plan.getMaxActiveJobs()))
                .thenReturn(1);

        assertDoesNotThrow(() -> quotaGuard.validateAndIncrementActiveJobs(companyId));
        verify(quotaRepository)
                .atomicIncrementIfUnderLimit(subscription.getId(), plan.getMaxActiveJobs());
    }

    @Test
    @DisplayName("Should issue atomic SQL decrement (floor enforced in DB via GREATEST)")
    void decrementActiveJobs_delegatesToAtomicSql() {
        when(subscriptionRepository.findActiveByCompanyId(companyId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));

        quotaGuard.decrementActiveJobs(companyId);

        verify(quotaRepository).atomicDecrementActiveJobs(subscription.getId());
        verify(quotaRepository, never()).save(any());
    }
}
