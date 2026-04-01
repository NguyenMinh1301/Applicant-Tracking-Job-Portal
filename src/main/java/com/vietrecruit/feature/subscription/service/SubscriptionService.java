package com.vietrecruit.feature.subscription.service;

import java.util.UUID;

import com.vietrecruit.common.enums.BillingCycle;
import com.vietrecruit.feature.subscription.dto.response.QuotaResponse;
import com.vietrecruit.feature.subscription.dto.response.SubscriptionResponse;
import com.vietrecruit.feature.subscription.entity.SubscriptionPlan;

public interface SubscriptionService {

    /**
     * Activates a new subscription for the given company, deactivating any existing active
     * subscription and setting the billing cycle and quota.
     *
     * @param companyId the subscribing company's UUID
     * @param plan the subscription plan to activate
     * @param cycle the billing cycle (MONTHLY or YEARLY)
     * @return the activated subscription response
     */
    SubscriptionResponse activateSubscription(
            UUID companyId, SubscriptionPlan plan, BillingCycle cycle);

    /**
     * Returns the currently active subscription for the given company.
     *
     * @param companyId the company's UUID
     * @return the active subscription response
     */
    SubscriptionResponse getCurrentSubscription(UUID companyId);

    /**
     * Returns the current job posting quota state (total, used, remaining) for the given company.
     *
     * @param companyId the company's UUID
     * @return the quota response
     */
    QuotaResponse getCurrentQuota(UUID companyId);

    /**
     * Cancels the active subscription for the given company.
     *
     * @param companyId the company's UUID
     */
    void cancelSubscription(UUID companyId);
}
