package com.vietrecruit.feature.subscription.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.subscription.entity.EmployerSubscription;
import com.vietrecruit.feature.subscription.entity.JobPostingQuota;
import com.vietrecruit.feature.subscription.repository.EmployerSubscriptionRepository;
import com.vietrecruit.feature.subscription.repository.JobPostingQuotaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class QuotaGuard {

    private final EmployerSubscriptionRepository subscriptionRepository;
    private final JobPostingQuotaRepository quotaRepository;

    /**
     * Validates that the company has a valid, non-expired subscription with remaining quota to
     * publish a job.
     */
    public void validateCanPublishJob(UUID companyId) {
        var subscription = getActiveSubscription(companyId);
        validateNotExpired(subscription);
        validateQuotaAvailable(subscription);
    }

    /**
     * Increments active job count and total posted count for the company's current quota. Call this
     * when a job transitions to PUBLISHED.
     */
    public void incrementActiveJobs(UUID companyId) {
        var subscription = getActiveSubscription(companyId);
        var quota = getQuota(subscription);
        quota.setJobsActive(quota.getJobsActive() + 1);
        quota.setJobsPosted(quota.getJobsPosted() + 1);
        quotaRepository.save(quota);
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
                .findActiveByCompanyId(companyId)
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
