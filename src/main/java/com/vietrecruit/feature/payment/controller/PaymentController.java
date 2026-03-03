package com.vietrecruit.feature.payment.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.payment.dto.request.CheckoutRequest;
import com.vietrecruit.feature.payment.dto.response.CheckoutResponse;
import com.vietrecruit.feature.payment.dto.response.PaymentStatusResponse;
import com.vietrecruit.feature.payment.service.PaymentService;
import com.vietrecruit.feature.user.repository.UserRepository;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Payment.ROOT)
@Tag(name = "Payment Service", description = "Endpoints for payment checkout and status")
public class PaymentController extends BaseController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    @Operation(
            summary = "Checkout",
            description = "Creates a PayOS payment link for the selected subscription plan")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Payment link created successfully")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Payment.CHECKOUT)
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request) {
        var companyId = resolveCompanyId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                ApiSuccessCode.CHECKOUT_SUCCESS,
                                paymentService.initiateCheckout(
                                        companyId,
                                        request.getPlanId(),
                                        request.getBillingCycle())));
    }

    @Operation(
            summary = "Get Payment Status",
            description = "Retrieves the current status of a payment transaction")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Payment status retrieved successfully")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.Payment.PAYMENT_STATUS)
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getPaymentStatus(
            @PathVariable Long orderCode) {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.PAYMENT_STATUS_FETCH_SUCCESS,
                        paymentService.getPaymentStatus(orderCode, companyId)));
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
