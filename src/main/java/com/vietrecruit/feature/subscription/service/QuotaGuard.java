package com.vietrecruit.feature.subscription.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.subscription.entity.EmployerSubscription;
import com.vietrecruit.feature.subscription.entity.JobPostingQuota;
import com.vietrecruit.feature.subscription.enums.SubscriptionStatus;
import com.vietrecruit.feature.subscription.repository.EmployerSubscriptionRepository;
import com.vietrecruit.feature.subscription.repository.JobPostingQuotaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaGuard {

    private final EmployerSubscriptionRepository subscriptionRepository;
    private final JobPostingQuotaRepository quotaRepository;

    /**
     * Atomically validates quota availability and increments active job count in a single DB
     * statement. Eliminates the TOCTOU race between separate validate and increment calls.
     *
     * @throws ApiException with QUOTA_EXCEEDED if the limit is reached
     * @throws ApiException with SUBSCRIPTION_EXPIRED if subscription has expired
     */
    public void validateAndIncrementActiveJobs(UUID companyId) {
        var subscription = getActiveSubscription(companyId);
        validateNotExpired(subscription);

        int maxActiveJobs = subscription.getPlan().getMaxActiveJobs();
        int updated =
                quotaRepository.atomicIncrementIfUnderLimit(subscription.getId(), maxActiveJobs);
        if (updated == 0) {
            throw new ApiException(
                    ApiErrorCode.QUOTA_EXCEEDED,
                    String.format("Active job limit reached. Upgrade your plan for more."));
        }
    }

    /**
     * Decrements active job count for the company's current quota. Call this when a job transitions
     * to CLOSED or is deleted.
     */
    public void decrementActiveJobs(UUID companyId) {
        var subscription = getActiveSubscription(companyId);
        var quota = getQuota(subscription);
        quota.setJobsActive(Math.max(0, quota.getJobsActive() - 1));
        quotaRepository.save(quota);
    }

    private EmployerSubscription getActiveSubscription(UUID companyId) {
        return subscriptionRepository
                .findActiveByCompanyId(companyId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ApiErrorCode.SUBSCRIPTION_REQUIRED));
    }

    private void validateNotExpired(EmployerSubscription subscription) {
        if (subscription.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ApiErrorCode.SUBSCRIPTION_EXPIRED);
        }
    }

    private void validateQuotaAvailable(EmployerSubscription subscription) {
        var plan = subscription.getPlan();
        if (plan.getMaxActiveJobs() == -1) {
            return; // unlimited
        }

        var quota = getQuota(subscription);
        if (quota.getJobsActive() >= plan.getMaxActiveJobs()) {
            throw new ApiException(
                    ApiErrorCode.QUOTA_EXCEEDED,
                    String.format(
                            "Active job limit reached (%d/%d). Upgrade your plan for more.",
                            quota.getJobsActive(), plan.getMaxActiveJobs()));
        }
    }

    private JobPostingQuota getQuota(EmployerSubscription subscription) {
        return quotaRepository
                .findBySubscriptionId(subscription.getId())
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ApiErrorCode.INTERNAL_ERROR,
                                        "Quota record missing for subscription "
                                                + subscription.getId()));
    }
}
