package com.vietrecruit.feature.subscription.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.subscription.dto.response.PlanResponse;
import com.vietrecruit.feature.subscription.mapper.SubscriptionMapper;
import com.vietrecruit.feature.subscription.repository.SubscriptionPlanRepository;
import com.vietrecruit.feature.subscription.service.PlanService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanServiceImpl implements PlanService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionMapper mapper;

    @Override
    public List<PlanResponse> listActivePlans() {
        return planRepository.findAllByIsActiveTrue().stream().map(mapper::toPlanResponse).toList();
    }

    @Override
    public PlanResponse getPlan(UUID planId) {
        var plan =
                planRepository
                        .findById(planId)
                        .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PLAN_NOT_FOUND));
        return mapper.toPlanResponse(plan);
    }
}
