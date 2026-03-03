package com.vietrecruit.feature.user.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.user.dto.request.UpdateProfileRequest;
import com.vietrecruit.feature.user.dto.response.UserProfileResponse;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.mapper.UserMapper;
import com.vietrecruit.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ClientUserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @InjectMocks private ClientUserServiceImpl clientUserService;

    private UUID userId;
    private User testUser;
    private UserProfileResponse profileResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser =
                User.builder().id(userId).email("test@example.com").fullName("Test User").build();
        profileResponse =
                UserProfileResponse.builder()
                        .id(userId)
                        .email("test@example.com")
                        .fullName("Test User")
                        .build();
    }

    @Test
    @DisplayName("Should get own profile via SecurityUtils")
    void getProfile_Success() {
        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(userRepository.findByIdWithRolesAndPermissions(userId))
                    .thenReturn(Optional.of(testUser));
            when(userMapper.toProfileResponse(testUser)).thenReturn(profileResponse);

            UserProfileResponse result = clientUserService.getProfile();

            assertNotNull(result);
            assertEquals("test@example.com", result.getEmail());
        }
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when user does not exist")
    void getProfile_NotFound() {
        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(userRepository.findByIdWithRolesAndPermissions(userId))
                    .thenReturn(Optional.empty());

            ApiException ex =
                    assertThrows(ApiException.class, () -> clientUserService.getProfile());
            assertEquals(ApiErrorCode.NOT_FOUND, ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("Should update own profile")
    void updateProfile_Success() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Name");

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            doNothing().when(userMapper).updateProfile(testUser, request);
            when(userRepository.save(any(User.class))).thenReturn(testUser);
            when(userMapper.toProfileResponse(testUser)).thenReturn(profileResponse);

            UserProfileResponse result = clientUserService.updateProfile(request);

            assertNotNull(result);
            verify(userMapper).updateProfile(testUser, request);
            verify(userRepository).save(testUser);
        }
    }
}
