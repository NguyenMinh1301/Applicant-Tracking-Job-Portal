package com.vietrecruit.feature.subscription.service;

import java.util.UUID;

import com.vietrecruit.feature.subscription.dto.response.QuotaResponse;
import com.vietrecruit.feature.subscription.dto.response.SubscriptionResponse;

public interface SubscriptionService {

    SubscriptionResponse subscribe(UUID companyId, UUID planId);

    SubscriptionResponse getCurrentSubscription(UUID companyId);

    QuotaResponse getCurrentQuota(UUID companyId);

    void cancelSubscription(UUID companyId);
}
