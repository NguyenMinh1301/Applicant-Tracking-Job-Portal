package com.vietrecruit.feature.user.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.user.dto.request.UserRequest;
import com.vietrecruit.feature.user.dto.response.AdminUserResponse;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.mapper.UserMapper;
import com.vietrecruit.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @InjectMocks private AdminUserServiceImpl adminUserService;

    private UUID userId;
    private User testUser;
    private UserRequest testRequest;
    private AdminUserResponse adminResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder().id(userId).email("test@example.com").fullName("Test").build();
        testRequest = new UserRequest();
        testRequest.setEmail("test@example.com");
        testRequest.setFullName("Test");
        adminResponse =
                AdminUserResponse.builder()
                        .id(userId)
                        .email("test@example.com")
                        .fullName("Test")
                        .build();
    }

    @Test
    @DisplayName("Should create user")
    void create_Success() {
        when(userRepository.existsByEmail(testRequest.getEmail())).thenReturn(false);
        when(userMapper.toEntity(testRequest)).thenReturn(testUser);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toAdminResponse(testUser)).thenReturn(adminResponse);

        AdminUserResponse result = adminUserService.create(testRequest);

        assertNotNull(result);
        assertEquals(userId, result.getId());
    }

    @Test
    @DisplayName("Should throw on duplicate email")
    void create_EmailConflict() {
        when(userRepository.existsByEmail(testRequest.getEmail())).thenReturn(true);

        ApiException ex =
                assertThrows(ApiException.class, () -> adminUserService.create(testRequest));
        assertEquals(ApiErrorCode.USER_EMAIL_CONFLICT, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should get user by ID")
    void get_Success() {
        when(userRepository.findByIdWithRolesAndPermissions(userId))
                .thenReturn(Optional.of(testUser));
        when(userMapper.toAdminResponse(testUser)).thenReturn(adminResponse);

        AdminUserResponse result = adminUserService.get(userId);

        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    @DisplayName("Should list users")
    void list_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(testUser));
        when(userRepository.findAll(pageable)).thenReturn(page);
        when(userMapper.toAdminResponse(testUser)).thenReturn(adminResponse);

        Page<AdminUserResponse> result = adminUserService.list(pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Should update user")
    void update_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        doNothing().when(userMapper).updateEntity(testUser, testRequest);
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(userMapper.toAdminResponse(testUser)).thenReturn(adminResponse);

        AdminUserResponse result = adminUserService.update(userId, testRequest);

        assertNotNull(result);
        verify(userMapper).updateEntity(testUser, testRequest);
    }

    @Test
    @DisplayName("Should delete user")
    void delete_Success() {
        when(userRepository.existsById(userId)).thenReturn(true);

        assertDoesNotThrow(() -> adminUserService.delete(userId));
        verify(userRepository).deleteById(userId);
    }

    @Test
    @DisplayName("Should throw NOT_FOUND on delete")
    void delete_NotFound() {
        when(userRepository.existsById(userId)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> adminUserService.delete(userId));
        assertEquals(ApiErrorCode.NOT_FOUND, ex.getErrorCode());
    }
}
