package com.vietrecruit.feature.auth.service;

import com.vietrecruit.feature.auth.dto.request.ForgotPasswordRequest;
import com.vietrecruit.feature.auth.dto.request.LoginRequest;
import com.vietrecruit.feature.auth.dto.request.RegisterRequest;
import com.vietrecruit.feature.auth.dto.request.ResendVerificationRequest;
import com.vietrecruit.feature.auth.dto.request.TokenRefreshRequest;
import com.vietrecruit.feature.auth.dto.response.LoginResponse;
import com.vietrecruit.feature.auth.dto.response.TokenRefreshResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    void register(RegisterRequest request);

    TokenRefreshResponse refresh(TokenRefreshRequest request);

    void logout(String accessToken);

    void forgotPassword(ForgotPasswordRequest request);

    void verifyEmail(String token);

    void resendVerification(ResendVerificationRequest request);

    LoginResponse processOAuth2Login(
            String provider,
            String email,
            String providerUserId,
            String providerName,
            String providerAvatarUrl);
}
