package com.vietrecruit.feature.subscription.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.feature.subscription.dto.response.QuotaResponse;
import com.vietrecruit.feature.subscription.dto.response.SubscriptionResponse;
import com.vietrecruit.feature.subscription.service.SubscriptionService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Subscription.ROOT)
@Tag(name = "Subscription Service", description = "Endpoints for managing employer subscriptions")
public class SubscriptionController extends BaseController {

    private final SubscriptionService subscriptionService;

    @Operation(
            summary = "Get Current Subscription",
            description = "Retrieves the current active subscription for the user's company")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Subscription retrieved successfully")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR')")
    @GetMapping(ApiConstants.Subscription.CURRENT)
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getCurrentSubscription() {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.SUBSCRIPTION_FETCH_SUCCESS,
                        subscriptionService.getCurrentSubscription(companyId)));
    }

    @Operation(
            summary = "Get Quota Usage",
            description = "Retrieves the current quota usage for the user's company subscription")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Quota retrieved successfully")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR')")
    @GetMapping(ApiConstants.Subscription.QUOTA)
    public ResponseEntity<ApiResponse<QuotaResponse>> getCurrentQuota() {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.QUOTA_FETCH_SUCCESS,
                        subscriptionService.getCurrentQuota(companyId)));
    }

    @Operation(
            summary = "Cancel Subscription",
            description = "Cancels the current active subscription for the user's company")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Subscription cancelled successfully")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @PutMapping(ApiConstants.Subscription.CANCEL)
    public ResponseEntity<ApiResponse<Void>> cancelSubscription() {
        var companyId = resolveCompanyId();
        subscriptionService.cancelSubscription(companyId);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.SUBSCRIPTION_CANCEL_SUCCESS));
    }
}
