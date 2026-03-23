package com.vietrecruit.feature.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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
import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.exception.GlobalExceptionHandler;
import com.vietrecruit.feature.auth.dto.request.ForgotPasswordRequest;
import com.vietrecruit.feature.auth.dto.request.LoginRequest;
import com.vietrecruit.feature.auth.dto.request.RegisterRequest;
import com.vietrecruit.feature.auth.dto.request.ResendOtpRequest;
import com.vietrecruit.feature.auth.dto.request.TokenRefreshRequest;
import com.vietrecruit.feature.auth.dto.request.VerifyOtpRequest;
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
        request.setPassword("Password123!");
        request.setFullName("New User");

        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(java.util.Collections.emptyMap());

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
    @DisplayName("Should verify OTP successfully")
    void verifyOtp_Success() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest("test@example.com", "12345678");

        doNothing().when(authService).verifyOtp(any(VerifyOtpRequest.class));

        mockMvc.perform(
                        post(ApiConstants.Auth.ROOT + ApiConstants.Auth.VERIFY_OTP)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_VERIFY_SUCCESS"));
    }

    @Test
    @DisplayName("Should return 400 for invalid OTP code")
    void verifyOtp_InvalidCode() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest("test@example.com", "99999999");

        doThrow(new ApiException(ApiErrorCode.AUTH_OTP_INVALID))
                .when(authService)
                .verifyOtp(any(VerifyOtpRequest.class));

        mockMvc.perform(
                        post(ApiConstants.Auth.ROOT + ApiConstants.Auth.VERIFY_OTP)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_OTP_INVALID"));
    }

    @Test
    @DisplayName("Should resend OTP successfully")
    void resendOtp_Success() throws Exception {
        ResendOtpRequest request = new ResendOtpRequest("test@example.com");

        doNothing().when(authService).resendOtp(any(ResendOtpRequest.class));

        mockMvc.perform(
                        post(ApiConstants.Auth.ROOT + ApiConstants.Auth.RESEND_OTP)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("AUTH_VERIFY_RESENT"));
    }
}
