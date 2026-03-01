package com.vietrecruit.feature.subscription.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.subscription.dto.response.QuotaResponse;
import com.vietrecruit.feature.subscription.dto.response.SubscriptionResponse;
import com.vietrecruit.feature.subscription.entity.EmployerSubscription;
import com.vietrecruit.feature.subscription.entity.JobPostingQuota;
import com.vietrecruit.feature.subscription.entity.SubscriptionStatus;
import com.vietrecruit.feature.subscription.mapper.SubscriptionMapper;
import com.vietrecruit.feature.subscription.repository.EmployerSubscriptionRepository;
import com.vietrecruit.feature.subscription.repository.JobPostingQuotaRepository;
import com.vietrecruit.feature.subscription.repository.SubscriptionPlanRepository;
import com.vietrecruit.feature.subscription.service.SubscriptionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final int DEFAULT_BILLING_DAYS = 30;

    private final SubscriptionPlanRepository planRepository;
    private final EmployerSubscriptionRepository subscriptionRepository;
    private final JobPostingQuotaRepository quotaRepository;
    private final SubscriptionMapper mapper;

    @Override
    @Transactional
    public SubscriptionResponse subscribe(UUID companyId, UUID planId) {
        var plan =
                planRepository
                        .findById(planId)
                        .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PLAN_NOT_FOUND));

        var now = Instant.now();
        var expiresAt = now.plus(DEFAULT_BILLING_DAYS, ChronoUnit.DAYS);

        var subscription =
                EmployerSubscription.builder()
                        .companyId(companyId)
                        .plan(plan)
                        .status(SubscriptionStatus.ACTIVE)
                        .startedAt(now)
                        .expiresAt(expiresAt)
                        .build();

        try {
            subscription = subscriptionRepository.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ApiErrorCode.SUBSCRIPTION_ALREADY_ACTIVE);
        }

        var quota =
                JobPostingQuota.builder()
                        .subscription(subscription)
                        .jobsPosted(0)
                        .jobsActive(0)
                        .cycleStart(now)
                        .cycleEnd(expiresAt)
                        .build();
        quotaRepository.save(quota);

        return mapper.toSubscriptionResponse(subscription);
    }

    @Override
    public SubscriptionResponse getCurrentSubscription(UUID companyId) {
        var subscription =
                subscriptionRepository
                        .findActiveByCompanyId(companyId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.SUBSCRIPTION_REQUIRED));
        return mapper.toSubscriptionResponse(subscription);
    }

    @Override
    public QuotaResponse getCurrentQuota(UUID companyId) {
        var subscription =
                subscriptionRepository
                        .findActiveByCompanyId(companyId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.SUBSCRIPTION_REQUIRED));

        var quota =
                quotaRepository
                        .findBySubscriptionId(subscription.getId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ApiErrorCode.INTERNAL_ERROR,
                                                "Quota record missing"));

        return mapper.toQuotaResponse(quota, subscription.getPlan());
    }

    @Override
    @Transactional
    public void cancelSubscription(UUID companyId) {
        var subscription =
                subscriptionRepository
                        .findActiveByCompanyId(companyId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.SUBSCRIPTION_REQUIRED));

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(Instant.now());
        subscriptionRepository.save(subscription);
    }
}
