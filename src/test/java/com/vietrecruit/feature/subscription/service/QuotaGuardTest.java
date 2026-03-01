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

import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.subscription.entity.EmployerSubscription;
import com.vietrecruit.feature.subscription.entity.JobPostingQuota;
import com.vietrecruit.feature.subscription.entity.SubscriptionPlan;
import com.vietrecruit.feature.subscription.entity.SubscriptionStatus;
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
    @DisplayName("Should pass validation with valid subscription and remaining quota")
    void validateCanPublishJob_Success() {
        when(subscriptionRepository.findActiveByCompanyId(companyId))
                .thenReturn(Optional.of(subscription));
        when(quotaRepository.findBySubscriptionId(subscription.getId()))
                .thenReturn(Optional.of(quota));

        assertDoesNotThrow(() -> quotaGuard.validateCanPublishJob(companyId));
    }

    @Test
    @DisplayName("Should throw SUBSCRIPTION_REQUIRED when no active subscription")
    void validateCanPublishJob_NoSubscription() {
        when(subscriptionRepository.findActiveByCompanyId(companyId)).thenReturn(Optional.empty());

        var ex =
                assertThrows(ApiException.class, () -> quotaGuard.validateCanPublishJob(companyId));
        assertEquals(ApiErrorCode.SUBSCRIPTION_REQUIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should throw SUBSCRIPTION_EXPIRED when subscription is past expiry")
    void validateCanPublishJob_Expired() {
        subscription.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(subscriptionRepository.findActiveByCompanyId(companyId))
                .thenReturn(Optional.of(subscription));

        var ex =
                assertThrows(ApiException.class, () -> quotaGuard.validateCanPublishJob(companyId));
        assertEquals(ApiErrorCode.SUBSCRIPTION_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should throw QUOTA_EXCEEDED when active jobs at limit")
    void validateCanPublishJob_QuotaExceeded() {
        quota.setJobsActive(5); // equals maxActiveJobs
        when(subscriptionRepository.findActiveByCompanyId(companyId))
                .thenReturn(Optional.of(subscription));
        when(quotaRepository.findBySubscriptionId(subscription.getId()))
                .thenReturn(Optional.of(quota));

        var ex =
                assertThrows(ApiException.class, () -> quotaGuard.validateCanPublishJob(companyId));
        assertEquals(ApiErrorCode.QUOTA_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should skip quota check for unlimited plan")
    void validateCanPublishJob_UnlimitedPlan() {
        plan.setMaxActiveJobs(-1);
        quota.setJobsActive(999);
        when(subscriptionRepository.findActiveByCompanyId(companyId))
                .thenReturn(Optional.of(subscription));

        assertDoesNotThrow(() -> quotaGuard.validateCanPublishJob(companyId));
        verify(quotaRepository, never()).findBySubscriptionId(any());
    }

    @Test
    @DisplayName("Should increment active and posted counts")
    void incrementActiveJobs_Success() {
        when(subscriptionRepository.findActiveByCompanyId(companyId))
                .thenReturn(Optional.of(subscription));
        when(quotaRepository.findBySubscriptionId(subscription.getId()))
                .thenReturn(Optional.of(quota));

        quotaGuard.incrementActiveJobs(companyId);

        assertEquals(3, quota.getJobsActive());
        assertEquals(11, quota.getJobsPosted());
        verify(quotaRepository).save(quota);
    }

    @Test
    @DisplayName("Should decrement active count with floor at zero")
    void decrementActiveJobs_FloorAtZero() {
        quota.setJobsActive(0);
        when(subscriptionRepository.findActiveByCompanyId(companyId))
                .thenReturn(Optional.of(subscription));
        when(quotaRepository.findBySubscriptionId(subscription.getId()))
                .thenReturn(Optional.of(quota));

        quotaGuard.decrementActiveJobs(companyId);

        assertEquals(0, quota.getJobsActive());
        verify(quotaRepository).save(quota);
    }
}
