package com.vietrecruit.feature.subscription.service;

import java.util.List;
import java.util.UUID;

import com.vietrecruit.feature.subscription.dto.response.PlanResponse;

public interface PlanService {

    /**
     * Returns all currently active subscription plans available for purchase.
     *
     * @return list of active plan responses
     */
    List<PlanResponse> listActivePlans();

    /**
     * Returns a single subscription plan by its UUID.
     *
     * @param planId the target plan's UUID
     * @return the plan response
     */
    PlanResponse getPlan(UUID planId);
}
