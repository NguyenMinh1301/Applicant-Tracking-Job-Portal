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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Auth.ROOT)
@Tag(name = "Auth Service", description = "Authentication and Authorization endpoints")
public class AuthController extends BaseController {

    private final AuthService authService;

    @Operation(summary = "User Login", description = "Authenticates a user and returns a JWT token")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Login successful")
    @RateLimiter(name = "authStrict", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.LOGIN)
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.AUTH_LOGIN_SUCCESS, authService.login(request)));
    }

    @Operation(summary = "User Registration", description = "Registers a new user account")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Registration successful")
    @RateLimiter(name = "authStrict", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.REGISTER)
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.AUTH_REGISTER_SUCCESS));
    }

    @Operation(
            summary = "Refresh Token",
            description = "Refreshes the JWT access token using a refresh token")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Token refreshed successfully")
    @RateLimiter(name = "authModerate", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.REFRESH)
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.AUTH_REFRESH_SUCCESS, authService.refresh(request)));
    }

    @Operation(summary = "User Logout", description = "Logs out the user and invalidates the token")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Logout successful")
    @RateLimiter(name = "authModerate", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.LOGOUT)
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.AUTH_LOGOUT_SUCCESS));
    }

    @Operation(summary = "Forgot Password", description = "Initiates the password reset process")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Password reset email sent")
    @RateLimiter(name = "authStrict", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.FORGOT_PASSWORD)
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.AUTH_FORGOT_SUCCESS));
    }

    @Operation(
            summary = "Verify Email",
            description = "Verifies the user's email address using a token")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Email verified successfully")
    @GetMapping(ApiConstants.Auth.VERIFY_EMAIL)
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.AUTH_VERIFY_SUCCESS));
    }

    @Operation(summary = "Resend Verification", description = "Resends the email verification link")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Verification email resent")
    @RateLimiter(name = "authStrict", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Auth.RESEND_VERIFICATION)
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.AUTH_VERIFY_RESENT));
    }
}
