package com.vietrecruit.feature.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import com.vietrecruit.common.exception.GlobalExceptionHandler;
import com.vietrecruit.feature.user.dto.request.UpdateProfileRequest;
import com.vietrecruit.feature.user.dto.response.UserProfileResponse;
import com.vietrecruit.feature.user.service.ClientUserService;

@ExtendWith(MockitoExtension.class)
class ClientUserControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private ClientUserService clientUserService;
    @InjectMocks private ClientUserController clientUserController;

    private UserProfileResponse profileResponse;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(clientUserController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        profileResponse =
                UserProfileResponse.builder()
                        .email("test@example.com")
                        .fullName("Test User")
                        .build();
    }

    @Test
    @DisplayName("Should get own profile")
    void getProfile_Success() throws Exception {
        when(clientUserService.getProfile()).thenReturn(profileResponse);

        mockMvc.perform(
                        get(ApiConstants.ClientUser.ROOT + ApiConstants.ClientUser.ME)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @DisplayName("Should update own profile")
    void updateProfile_Success() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");

        when(clientUserService.updateProfile(any(UpdateProfileRequest.class)))
                .thenReturn(profileResponse);

        mockMvc.perform(
                        put(ApiConstants.ClientUser.ROOT + ApiConstants.ClientUser.ME)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
