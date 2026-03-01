package com.vietrecruit.feature.subscription.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.subscription.dto.response.PlanResponse;
import com.vietrecruit.feature.subscription.entity.SubscriptionPlan;
import com.vietrecruit.feature.subscription.mapper.SubscriptionMapper;
import com.vietrecruit.feature.subscription.repository.SubscriptionPlanRepository;

@ExtendWith(MockitoExtension.class)
class PlanServiceImplTest {

    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionMapper mapper;
    @InjectMocks private PlanServiceImpl planService;

    private SubscriptionPlan plan;
    private PlanResponse planResponse;
    private UUID planId;

    @BeforeEach
    void setUp() {
        planId = UUID.randomUUID();
        plan =
                SubscriptionPlan.builder()
                        .id(planId)
                        .code("BASIC")
                        .name("Basic")
                        .maxActiveJobs(5)
                        .jobDurationDays(30)
                        .priceMonthly(BigDecimal.valueOf(500000))
                        .isActive(true)
                        .build();

        planResponse =
                PlanResponse.builder()
                        .id(planId)
                        .code("BASIC")
                        .name("Basic")
                        .maxActiveJobs(5)
                        .jobDurationDays(30)
                        .priceMonthly(BigDecimal.valueOf(500000))
                        .build();
    }

    @Test
    @DisplayName("Should list active plans")
    void listActivePlans_Success() {
        when(planRepository.findAllByIsActiveTrue()).thenReturn(List.of(plan));
        when(mapper.toPlanResponse(plan)).thenReturn(planResponse);

        var result = planService.listActivePlans();

        assertEquals(1, result.size());
        assertEquals("BASIC", result.get(0).getCode());
    }

    @Test
    @DisplayName("Should get plan by ID")
    void getPlan_Success() {
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(mapper.toPlanResponse(plan)).thenReturn(planResponse);

        var result = planService.getPlan(planId);

        assertEquals("Basic", result.getName());
    }

    @Test
    @DisplayName("Should throw PLAN_NOT_FOUND for non-existent plan")
    void getPlan_NotFound() {
        when(planRepository.findById(planId)).thenReturn(Optional.empty());

        var ex = assertThrows(ApiException.class, () -> planService.getPlan(planId));
        assertEquals(ApiErrorCode.PLAN_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Should throw PLAN_NOT_FOUND for inactive plan")
    void getPlan_Inactive() {
        plan.setIsActive(false);
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        var ex = assertThrows(ApiException.class, () -> planService.getPlan(planId));
        assertEquals(ApiErrorCode.PLAN_NOT_FOUND, ex.getErrorCode());
    }
}
