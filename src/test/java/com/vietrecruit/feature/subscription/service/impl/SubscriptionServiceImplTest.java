package com.vietrecruit.feature.subscription.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.subscription.dto.response.QuotaResponse;
import com.vietrecruit.feature.subscription.dto.response.SubscriptionResponse;
import com.vietrecruit.feature.subscription.entity.EmployerSubscription;
import com.vietrecruit.feature.subscription.entity.JobPostingQuota;
import com.vietrecruit.feature.subscription.entity.SubscriptionPlan;
import com.vietrecruit.feature.subscription.entity.SubscriptionStatus;
import com.vietrecruit.feature.subscription.mapper.SubscriptionMapper;
import com.vietrecruit.feature.subscription.repository.EmployerSubscriptionRepository;
import com.vietrecruit.feature.subscription.repository.JobPostingQuotaRepository;
import com.vietrecruit.feature.subscription.repository.SubscriptionPlanRepository;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private EmployerSubscriptionRepository subscriptionRepository;
    @Mock private JobPostingQuotaRepository quotaRepository;
    @Mock private SubscriptionMapper mapper;
    @InjectMocks private SubscriptionServiceImpl subscriptionService;

    private UUID companyId;
    private UUID planId;
    private SubscriptionPlan plan;
    private EmployerSubscription subscription;
    private JobPostingQuota quota;
    private SubscriptionResponse subscriptionResponse;
    private QuotaResponse quotaResponse;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        planId = UUID.randomUUID();

        plan =
                SubscriptionPlan.builder()
                        .id(planId)
                        .code("BASIC")
                        .name("Basic")
                        .maxActiveJobs(5)
                        .isActive(true)
                        .build();

        subscription =
                EmployerSubscription.builder()
                        .id(UUID.randomUUID())
                        .companyId(companyId)
                        .plan(plan)
                        .status(SubscriptionStatus.ACTIVE)
                        .startedAt(Instant.now())
                        .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                        .build();

        quota =
                JobPostingQuota.builder()
                        .id(UUID.randomUUID())
                        .subscription(subscription)
                        .jobsActive(2)
                        .jobsPosted(10)
                        .build();

        subscriptionResponse =
                SubscriptionResponse.builder()
                        .id(subscription.getId())
                        .planName("Basic")
                        .planCode("BASIC")
                        .status("ACTIVE")
                        .build();

        quotaResponse =
                QuotaResponse.builder().maxActiveJobs(5).jobsActive(2).jobsPosted(10).build();
    }

    @Test
    @DisplayName("Should subscribe successfully")
    void subscribe_Success() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.saveAndFlush(any(EmployerSubscription.class)))
                .thenReturn(subscription);
        when(mapper.toSubscriptionResponse(any(EmployerSubscription.class)))
                .thenReturn(subscriptionResponse);

        var result = subscriptionService.subscribe(companyId, planId);

        assertNotNull(result);
        assertEquals("BASIC", result.getPlanCode());
        verify(quotaRepository).save(any(JobPostingQuota.class));
    }

    @Test
    @DisplayName("Should throw when subscribing with already active subscription")
    void subscribe_AlreadyActive() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(subscriptionRepository.saveAndFlush(any(EmployerSubscription.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        var ex =
                assertThrows(
                        ApiException.class, () -> subscriptionService.subscribe(companyId, planId));
        assertEquals(ApiErrorCode.SUBSCRIPTION_ALREADY_ACTIVE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should throw when subscribing to inactive plan")
    void subscribe_InactivePlan() {
        plan.setIsActive(false);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        var ex =
                assertThrows(
                        ApiException.class, () -> subscriptionService.subscribe(companyId, planId));
        assertEquals(ApiErrorCode.PLAN_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should get current subscription")
    void getCurrentSubscription_Success() {
        when(subscriptionRepository.findActiveByCompanyId(companyId))
                .thenReturn(Optional.of(subscription));
        when(mapper.toSubscriptionResponse(subscription)).thenReturn(subscriptionResponse);

        var result = subscriptionService.getCurrentSubscription(companyId);

        assertEquals("Basic", result.getPlanName());
    }

    @Test
    @DisplayName("Should throw when no active subscription for getCurrentSubscription")
    void getCurrentSubscription_NotFound() {
        when(subscriptionRepository.findActiveByCompanyId(companyId)).thenReturn(Optional.empty());

        var ex =
                assertThrows(
                        ApiException.class,
                        () -> subscriptionService.getCurrentSubscription(companyId));
        assertEquals(ApiErrorCode.SUBSCRIPTION_REQUIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should get current quota")
    void getCurrentQuota_Success() {
        when(subscriptionRepository.findActiveByCompanyId(companyId))
                .thenReturn(Optional.of(subscription));
        when(quotaRepository.findBySubscriptionId(subscription.getId()))
                .thenReturn(Optional.of(quota));
        when(mapper.toQuotaResponse(quota, plan)).thenReturn(quotaResponse);

        var result = subscriptionService.getCurrentQuota(companyId);

        assertEquals(5, result.getMaxActiveJobs());
        assertEquals(2, result.getJobsActive());
    }

    @Test
    @DisplayName("Should cancel subscription")
    void cancelSubscription_Success() {
        when(subscriptionRepository.findActiveByCompanyId(companyId))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.save(any(EmployerSubscription.class))).thenReturn(subscription);

        subscriptionService.cancelSubscription(companyId);

        var captor = ArgumentCaptor.forClass(EmployerSubscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertEquals(SubscriptionStatus.CANCELLED, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getCancelledAt());
    }
}
