package com.vietrecruit.feature.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.vietrecruit.feature.user.dto.response.UserResponse;
import com.vietrecruit.feature.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private UserService userService;

    @InjectMocks private UserController userController;

    private UUID userId;
    private UserRequest userRequest;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(userController)
                        .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();

        userId = UUID.randomUUID();

        userRequest = new UserRequest();
        userRequest.setEmail("test@example.com");
        userRequest.setFullName("Test User");

        userResponse =
                UserResponse.builder()
                        .id(userId)
                        .email("test@example.com")
                        .fullName("Test User")
                        .build();
    }

    @Test
    @DisplayName("Should create user successfully")
    void create_Success() throws Exception {
        when(userService.create(any(UserRequest.class))).thenReturn(userResponse);

        mockMvc.perform(
                        post(ApiConstants.User.ROOT + ApiConstants.User.CREATE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @DisplayName("Should get user by ID")
    void get_Success() throws Exception {
        when(userService.get(userId)).thenReturn(userResponse);

        mockMvc.perform(
                        get(ApiConstants.User.ROOT + ApiConstants.User.GET, userId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()));
    }

    @Test
    @DisplayName("Should list users with pagination")
    void list_Success() throws Exception {
        Page<UserResponse> pageResponse = new PageImpl<>(List.of(userResponse));
        when(userService.list(any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(
                        get(ApiConstants.User.ROOT)
                                .param("page", "0")
                                .param("size", "10")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(userId.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Should update user successfully")
    void update_Success() throws Exception {
        when(userService.update(eq(userId), any(UserRequest.class))).thenReturn(userResponse);

        mockMvc.perform(
                        put(ApiConstants.User.ROOT + ApiConstants.User.UPDATE, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(userRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()));
    }

    @Test
    @DisplayName("Should delete user successfully")
    void delete_Success() throws Exception {
        doNothing().when(userService).delete(userId);

        mockMvc.perform(
                        delete(ApiConstants.User.ROOT + ApiConstants.User.DELETE, userId)
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("USER_DELETE_SUCCESS"));
    }
}
