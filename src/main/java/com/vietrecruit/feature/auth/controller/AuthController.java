package com.vietrecruit.feature.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.response.ApiSuccessCode;
import com.vietrecruit.feature.auth.dto.request.ForgotPasswordRequest;
import com.vietrecruit.feature.auth.dto.request.LoginRequest;
import com.vietrecruit.feature.auth.dto.request.RegisterRequest;
import com.vietrecruit.feature.auth.dto.request.ResendVerificationRequest;
import com.vietrecruit.feature.auth.dto.request.TokenRefreshRequest;
import com.vietrecruit.feature.auth.dto.response.LoginResponse;
import com.vietrecruit.feature.auth.dto.response.TokenRefreshResponse;
import com.vietrecruit.feature.auth.service.AuthService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Auth.ROOT)
public class AuthController extends BaseController {

    private final AuthService authService;

    @RateLimiter(name = "authStrict", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.LOGIN)
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.AUTH_LOGIN_SUCCESS, authService.login(request)));
    }

    @RateLimiter(name = "authStrict", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.REGISTER)
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.AUTH_REGISTER_SUCCESS));
    }

    @RateLimiter(name = "authModerate", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.REFRESH)
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.AUTH_REFRESH_SUCCESS, authService.refresh(request)));
    }

    @RateLimiter(name = "authModerate", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.LOGOUT)
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.AUTH_LOGOUT_SUCCESS));
    }

    @RateLimiter(name = "authStrict", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.FORGOT_PASSWORD)
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.AUTH_FORGOT_SUCCESS));
    }

    @GetMapping(ApiConstants.Auth.VERIFY_EMAIL)
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.AUTH_VERIFY_SUCCESS));
    }

    @RateLimiter(name = "authStrict", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.RESEND_VERIFICATION)
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.AUTH_VERIFY_RESENT));
    }
}
