package com.vietrecruit.feature.subscription.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.config.cache.CacheNames;
import com.vietrecruit.common.enums.ApiErrorCode;
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
    @org.springframework.cache.annotation.Cacheable(value = CacheNames.PLAN_LIST, key = "'all'")
    public List<PlanResponse> listActivePlans() {
        // collect(toList()) returns a mutable ArrayList, avoiding ImmutableCollections$ListN
        // which cannot be deserialized by Jackson when pulled from Redis cache.
        return planRepository.findAllByIsActiveTrue().stream()
                .map(mapper::toPlanResponse)
                .collect(Collectors.toList());
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(value = CacheNames.PLAN_DETAIL, key = "#planId")
    public PlanResponse getPlan(UUID planId) {
        var plan =
                planRepository
                        .findById(planId)
                        .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                        .orElseThrow(() -> new ApiException(ApiErrorCode.PLAN_NOT_FOUND));
        return mapper.toPlanResponse(plan);
    }
}
