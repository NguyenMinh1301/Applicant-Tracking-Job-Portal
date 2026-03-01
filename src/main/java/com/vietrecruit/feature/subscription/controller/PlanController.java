package com.vietrecruit.feature.subscription.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.response.ApiSuccessCode;
import com.vietrecruit.feature.subscription.dto.response.PlanResponse;
import com.vietrecruit.feature.subscription.service.PlanService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Plan.ROOT)
@Tag(name = "Plan Service", description = "Public endpoints for viewing subscription plans")
public class PlanController extends BaseController {

    private final PlanService planService;

    @Operation(summary = "List Plans", description = "Retrieves all active subscription plans")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Plans retrieved successfully")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PlanResponse>>> listPlans() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.PLAN_LIST_SUCCESS, planService.listActivePlans()));
    }

    @Operation(
            summary = "Get Plan",
            description = "Retrieves details of a specific subscription plan")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Plan retrieved successfully")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.Plan.GET)
    public ResponseEntity<ApiResponse<PlanResponse>> getPlan(@PathVariable UUID planId) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.PLAN_FETCH_SUCCESS, planService.getPlan(planId)));
    }
}
