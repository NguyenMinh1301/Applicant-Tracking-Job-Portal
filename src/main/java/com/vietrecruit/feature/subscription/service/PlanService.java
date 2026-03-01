package com.vietrecruit.feature.subscription.service;

import java.util.List;
import java.util.UUID;

import com.vietrecruit.feature.subscription.dto.response.PlanResponse;

public interface PlanService {

    List<PlanResponse> listActivePlans();

    PlanResponse getPlan(UUID planId);
}
