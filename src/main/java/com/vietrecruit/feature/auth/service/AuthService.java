package com.vietrecruit.feature.auth.service;

import java.util.UUID;

import com.vietrecruit.feature.auth.dto.request.ChangePasswordRequest;
import com.vietrecruit.feature.auth.dto.request.ForgotPasswordRequest;
import com.vietrecruit.feature.auth.dto.request.LoginRequest;
import com.vietrecruit.feature.auth.dto.request.RegisterRequest;
import com.vietrecruit.feature.auth.dto.request.ResendOtpRequest;
import com.vietrecruit.feature.auth.dto.request.ResetPasswordRequest;
import com.vietrecruit.feature.auth.dto.request.TokenRefreshRequest;
import com.vietrecruit.feature.auth.dto.request.VerifyOtpRequest;
import com.vietrecruit.feature.auth.dto.response.LoginResponse;
import com.vietrecruit.feature.auth.dto.response.TokenRefreshResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    void register(RegisterRequest request);

    TokenRefreshResponse refresh(TokenRefreshRequest request);

    void logout(String accessToken);

    void forgotPassword(ForgotPasswordRequest request);

    void changePassword(UUID userId, ChangePasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void verifyOtp(VerifyOtpRequest request);

    void resendOtp(ResendOtpRequest request);

    LoginResponse processOAuth2Login(
            String provider,
            String email,
            String providerUserId,
            String providerName,
            String providerAvatarUrl,
            Boolean providerEmailVerified);
}
