package com.vietrecruit.feature.subscription.service;

import java.util.UUID;

import com.vietrecruit.common.enums.BillingCycle;
import com.vietrecruit.feature.subscription.dto.response.QuotaResponse;
import com.vietrecruit.feature.subscription.dto.response.SubscriptionResponse;
import com.vietrecruit.feature.subscription.entity.SubscriptionPlan;

public interface SubscriptionService {

    SubscriptionResponse activateSubscription(
            UUID companyId, SubscriptionPlan plan, BillingCycle cycle);

    SubscriptionResponse getCurrentSubscription(UUID companyId);

    QuotaResponse getCurrentQuota(UUID companyId);

    void cancelSubscription(UUID companyId);
}
