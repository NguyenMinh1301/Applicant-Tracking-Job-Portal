package com.vietrecruit.feature.subscription.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.response.ApiSuccessCode;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.subscription.dto.request.SubscribeRequest;
import com.vietrecruit.feature.subscription.dto.response.QuotaResponse;
import com.vietrecruit.feature.subscription.dto.response.SubscriptionResponse;
import com.vietrecruit.feature.subscription.service.SubscriptionService;
import com.vietrecruit.feature.user.repository.UserRepository;

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
    private final UserRepository userRepository;

    @Operation(
            summary = "Subscribe",
            description = "Subscribes the current user's company to a plan")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Subscription activated successfully")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionResponse>> subscribe(
            @Valid @RequestBody SubscribeRequest request) {
        var companyId = resolveCompanyId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                ApiSuccessCode.SUBSCRIPTION_CREATE_SUCCESS,
                                subscriptionService.subscribe(companyId, request.getPlanId())));
    }

    @Operation(
            summary = "Get Current Subscription",
            description = "Retrieves the current active subscription for the user's company")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Subscription retrieved successfully")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
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
    @PutMapping(ApiConstants.Subscription.CANCEL)
    public ResponseEntity<ApiResponse<Void>> cancelSubscription() {
        var companyId = resolveCompanyId();
        subscriptionService.cancelSubscription(companyId);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.SUBSCRIPTION_CANCEL_SUCCESS));
    }

    /**
     * Resolves the company ID from the currently authenticated user. Throws FORBIDDEN if the user
     * is not associated with a company.
     */
    private UUID resolveCompanyId() {
        var userId = SecurityUtils.getCurrentUserId();
        return userRepository
                .findById(userId)
                .map(
                        user -> {
                            if (user.getCompanyId() == null) {
                                throw new ApiException(
                                        ApiErrorCode.FORBIDDEN,
                                        "User is not associated with any company");
                            }
                            return user.getCompanyId();
                        })
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "User not found"));
    }
}
