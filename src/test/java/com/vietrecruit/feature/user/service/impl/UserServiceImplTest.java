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
import com.vietrecruit.feature.user.dto.response.UserResponse;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.mapper.UserMapper;
import com.vietrecruit.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;

    @Mock private UserMapper userMapper;

    @InjectMocks private UserServiceImpl userService;

    private User testUser;
    private UserRequest testRequest;
    private UserResponse testResponse;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser =
                User.builder().id(userId).email("test@example.com").fullName("Test User").build();

        testRequest = new UserRequest();
        testRequest.setEmail("test@example.com");
        testRequest.setFullName("Test User");

        testResponse =
                UserResponse.builder()
                        .id(userId)
                        .email("test@example.com")
                        .fullName("Test User")
                        .build();
    }

    @Test
    @DisplayName("Should create user successfully")
    void create_Success() {
        when(userRepository.existsByEmail(testRequest.getEmail())).thenReturn(false);
        when(userMapper.toEntity(testRequest)).thenReturn(testUser);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(testResponse);

        UserResponse response = userService.create(testRequest);

        assertNotNull(response);
        assertEquals(userId, response.getId());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw when email exists on create")
    void create_ThrowsEmailConflict() {
        when(userRepository.existsByEmail(testRequest.getEmail())).thenReturn(true);

        ApiException exception =
                assertThrows(ApiException.class, () -> userService.create(testRequest));
        assertEquals(ApiErrorCode.USER_EMAIL_CONFLICT, exception.getErrorCode());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should get user by ID successfully")
    void get_Success() {
        when(userRepository.findByIdWithRolesAndPermissions(userId))
                .thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testResponse);

        UserResponse response = userService.get(userId);

        assertNotNull(response);
        assertEquals("test@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when getting non-existent user")
    void get_ThrowsNotFound() {
        when(userRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> userService.get(userId));
        assertEquals(ApiErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should list users pageable")
    void list_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(testUser));

        when(userRepository.findAll(pageable)).thenReturn(page);
        when(userMapper.toResponse(testUser)).thenReturn(testResponse);

        Page<UserResponse> result = userService.list(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("test@example.com", result.getContent().get(0).getEmail());
    }

    @Test
    @DisplayName("Should update user successfully")
    void update_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        doNothing().when(userMapper).updateEntity(testUser, testRequest);
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(userMapper.toResponse(testUser)).thenReturn(testResponse);

        UserResponse response = userService.update(userId, testRequest);

        assertNotNull(response);
        verify(userMapper, times(1)).updateEntity(testUser, testRequest);
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("Should throw NOT_FOUND on update non-existent user")
    void update_ThrowsNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        ApiException exception =
                assertThrows(ApiException.class, () -> userService.update(userId, testRequest));
        assertEquals(ApiErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw USER_EMAIL_CONFLICT when updating to an existing email")
    void update_ThrowsEmailConflict() {
        testRequest.setEmail("conflict@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.existsByEmail("conflict@example.com")).thenReturn(true);

        ApiException exception =
                assertThrows(ApiException.class, () -> userService.update(userId, testRequest));
        assertEquals(ApiErrorCode.USER_EMAIL_CONFLICT, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should delete user successfully")
    void delete_Success() {
        when(userRepository.existsById(userId)).thenReturn(true);

        assertDoesNotThrow(() -> userService.delete(userId));

        verify(userRepository, times(1)).deleteById(userId);
    }

    @Test
    @DisplayName("Should throw NOT_FOUND on delete non-existent user")
    void delete_ThrowsNotFound() {
        when(userRepository.existsById(userId)).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> userService.delete(userId));
        assertEquals(ApiErrorCode.NOT_FOUND, exception.getErrorCode());

        verify(userRepository, never()).deleteById(any(UUID.class));
    }
}
