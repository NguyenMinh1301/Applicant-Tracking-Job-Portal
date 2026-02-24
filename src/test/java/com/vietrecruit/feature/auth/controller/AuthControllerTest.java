package com.vietrecruit.feature.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.exception.GlobalExceptionHandler;
import com.vietrecruit.feature.auth.dto.request.ForgotPasswordRequest;
import com.vietrecruit.feature.auth.dto.request.LoginRequest;
import com.vietrecruit.feature.auth.dto.request.RegisterRequest;
import com.vietrecruit.feature.auth.dto.request.ResendVerificationRequest;
import com.vietrecruit.feature.auth.dto.request.TokenRefreshRequest;
import com.vietrecruit.feature.auth.dto.response.LoginResponse;
import com.vietrecruit.feature.auth.dto.response.TokenRefreshResponse;
import com.vietrecruit.feature.auth.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private AuthService authService;

    @InjectMocks private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(authController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    @DisplayName("Should login successfully")
    void login_Success() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        LoginResponse response =
                LoginResponse.builder()
                        .accessToken("access.token")
                        .refreshToken("refresh.token")
                        .expiresIn(900L)
                        .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(
                        post(ApiConstants.Auth.ROOT + ApiConstants.Auth.LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh.token"));
    }

    @Test
    @DisplayName("Should register new user successfully")
    void register_Success() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setFullName("New User");

        doNothing().when(authService).register(any(RegisterRequest.class));

        mockMvc.perform(
                        post(ApiConstants.Auth.ROOT + ApiConstants.Auth.REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_REGISTER_SUCCESS"));
    }

    @Test
    @DisplayName("Should refresh token successfully")
    void refresh_Success() throws Exception {
        TokenRefreshRequest request = new TokenRefreshRequest();
        request.setRefreshToken("old.refresh.token");

        TokenRefreshResponse response =
                TokenRefreshResponse.builder()
                        .accessToken("new.access.token")
                        .refreshToken("new.refresh.token")
                        .expiresIn(900L)
                        .build();

        when(authService.refresh(any(TokenRefreshRequest.class))).thenReturn(response);

        mockMvc.perform(
                        post(ApiConstants.Auth.ROOT + ApiConstants.Auth.REFRESH)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new.access.token"));
    }

    @Test
    @DisplayName("Should logout successfully")
    void logout_Success() throws Exception {
        doNothing().when(authService).logout(anyString());

        mockMvc.perform(
                        post(ApiConstants.Auth.ROOT + ApiConstants.Auth.LOGOUT)
                                .header("Authorization", "Bearer token123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_LOGOUT_SUCCESS"));

        verify(authService, times(1)).logout("token123");
    }

    @Test
    @DisplayName("Should request password reset successfully")
    void forgotPassword_Success() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("test@example.com");

        doNothing().when(authService).forgotPassword(any(ForgotPasswordRequest.class));

        mockMvc.perform(
                        post(ApiConstants.Auth.ROOT + ApiConstants.Auth.FORGOT_PASSWORD)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_FORGOT_SUCCESS"));
    }

    @Test
    @DisplayName("Should verify email successfully")
    void verifyEmail_Success() throws Exception {
        doNothing().when(authService).verifyEmail("valid-token");

        mockMvc.perform(
                        get(ApiConstants.Auth.ROOT + ApiConstants.Auth.VERIFY_EMAIL)
                                .param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_VERIFY_SUCCESS"));
    }

    @Test
    @DisplayName("Should return 400 for invalid verification token")
    void verifyEmail_InvalidToken() throws Exception {
        doThrow(new ApiException(ApiErrorCode.AUTH_VERIFY_TOKEN_INVALID))
                .when(authService)
                .verifyEmail("bad-token");

        mockMvc.perform(
                        get(ApiConstants.Auth.ROOT + ApiConstants.Auth.VERIFY_EMAIL)
                                .param("token", "bad-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_VERIFY_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Should resend verification email successfully")
    void resendVerification_Success() throws Exception {
        ResendVerificationRequest request = new ResendVerificationRequest("test@example.com");

        doNothing().when(authService).resendVerification(any(ResendVerificationRequest.class));

        mockMvc.perform(
                        post(ApiConstants.Auth.ROOT + ApiConstants.Auth.RESEND_VERIFICATION)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_VERIFY_RESENT"));
    }
}
