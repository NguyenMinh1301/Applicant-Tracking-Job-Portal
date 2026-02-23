package com.vietrecruit.feature.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.exception.GlobalExceptionHandler;
import com.vietrecruit.feature.user.dto.request.UserRequest;
import com.vietrecruit.feature.user.dto.response.AdminUserResponse;
import com.vietrecruit.feature.user.service.AdminUserService;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private AdminUserService adminUserService;
    @InjectMocks private AdminUserController adminUserController;

    private UUID userId;
    private UserRequest userRequest;
    private AdminUserResponse adminResponse;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(adminUserController)
                        .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        userId = UUID.randomUUID();
        userRequest = new UserRequest();
        userRequest.setEmail("test@example.com");
        userRequest.setFullName("Test User");

        adminResponse =
                AdminUserResponse.builder()
                        .id(userId)
                        .email("test@example.com")
                        .fullName("Test User")
                        .build();
    }

    @Test
    @DisplayName("Should create user")
    void create_Success() throws Exception {
        when(adminUserService.create(any(UserRequest.class))).thenReturn(adminResponse);

        mockMvc.perform(
                        post(ApiConstants.AdminUser.ROOT + ApiConstants.AdminUser.CREATE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()));
    }

    @Test
    @DisplayName("Should get user by ID")
    void get_Success() throws Exception {
        when(adminUserService.get(userId)).thenReturn(adminResponse);

        mockMvc.perform(
                        get(ApiConstants.AdminUser.ROOT + "/" + userId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()));
    }

    @Test
    @DisplayName("Should list users")
    void list_Success() throws Exception {
        Page<AdminUserResponse> page = new PageImpl<>(List.of(adminResponse));
        when(adminUserService.list(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(
                        get(ApiConstants.AdminUser.ROOT)
                                .param("page", "0")
                                .param("size", "10")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Should update user")
    void update_Success() throws Exception {
        when(adminUserService.update(eq(userId), any(UserRequest.class))).thenReturn(adminResponse);

        mockMvc.perform(
                        put(ApiConstants.AdminUser.ROOT + "/" + userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Should delete user")
    void delete_Success() throws Exception {
        doNothing().when(adminUserService).delete(userId);

        mockMvc.perform(
                        delete(ApiConstants.AdminUser.ROOT + "/" + userId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("USER_DELETE_SUCCESS"));
    }
}
