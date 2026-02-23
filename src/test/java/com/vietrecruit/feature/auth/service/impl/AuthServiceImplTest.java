package com.vietrecruit.feature.auth.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.security.AuthCacheService;
import com.vietrecruit.common.security.JwtService;
import com.vietrecruit.feature.auth.dto.request.LoginRequest;
import com.vietrecruit.feature.auth.dto.request.RegisterRequest;
import com.vietrecruit.feature.auth.dto.response.LoginResponse;
import com.vietrecruit.feature.auth.entity.RefreshToken;
import com.vietrecruit.feature.auth.repository.RefreshTokenRepository;
import com.vietrecruit.feature.user.entity.Permission;
import com.vietrecruit.feature.user.entity.Role;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.repository.RoleRepository;
import com.vietrecruit.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;

    @Mock private RoleRepository roleRepository;

    @Mock private RefreshTokenRepository refreshTokenRepository;

    @Mock private PasswordEncoder passwordEncoder;

    @Mock private JwtService jwtService;

    @Mock private AuthCacheService authCacheService;

    @InjectMocks private AuthServiceImpl authService;

    private User testUser;
    private Role testRole;
    private Permission testPermission;

    @BeforeEach
    void setUp() {
        testPermission = new Permission();
        testPermission.setCode("USER:READ");

        testRole = new Role();
        testRole.setCode("CANDIDATE");
        testRole.getPermissions().add(testPermission);

        testUser =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("test@example.com")
                        .passwordHash("hashedPwd")
                        .isActive(true)
                        .isLocked(false)
                        .failedAttempts((short) 0)
                        .build();
        testUser.getRoles().add(testRole);
    }

    @Test
    @DisplayName("Should login successfully and return tokens")
    void login_Success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.getPassword(), testUser.getPasswordHash()))
                .thenReturn(true);
        when(userRepository.findByIdWithRolesAndPermissions(testUser.getId()))
                .thenReturn(Optional.of(testUser));

        when(jwtService.generateAccessToken(eq(testUser.getId()), anySet()))
                .thenReturn("access.token.here");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access.token.here", response.getAccessToken());
        assertEquals("raw-refresh-token", response.getRefreshToken());
        assertEquals(900L, response.getExpiresIn());

        verify(userRepository, times(1)).save(testUser); // saves reset attempts
        verify(authCacheService, times(1)).cachePermissions(eq(testUser.getId()), anySet());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));

        assertEquals(0, testUser.getFailedAttempts().intValue());
        assertFalse(testUser.getIsLocked());
    }

    @Test
    @DisplayName(
            "Should throw AUTH_INVALID_CREDENTIALS when password mismatch and increment failed attempts")
    void login_InvalidPassword_IncrementsFailedAttempts() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongpassword");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.getPassword(), testUser.getPasswordHash()))
                .thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> authService.login(request));
        assertEquals(ApiErrorCode.AUTH_INVALID_CREDENTIALS, exception.getErrorCode());

        assertEquals(1, testUser.getFailedAttempts().intValue());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("Should lock account after max failed attempts")
    void login_MaxFailedAttempts_LocksAccount() {
        testUser.setFailedAttempts((short) 4);
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongpassword");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(request.getPassword(), testUser.getPasswordHash()))
                .thenReturn(false);

        assertThrows(ApiException.class, () -> authService.login(request));

        assertEquals(5, testUser.getFailedAttempts().intValue());
        assertTrue(testUser.getIsLocked());
        assertNotNull(testUser.getLockUntil());
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("Should throw AUTH_ACCOUNT_LOCKED when account is locked")
    void login_AccountLocked() {
        testUser.setIsLocked(true);
        testUser.setLockUntil(Instant.now().plusSeconds(3600));

        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));

        ApiException exception = assertThrows(ApiException.class, () -> authService.login(request));
        assertEquals(ApiErrorCode.AUTH_ACCOUNT_LOCKED, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should register new user successfully")
    void register_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setFullName("New User");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByCode("CANDIDATE")).thenReturn(Optional.of(testRole));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-new-pwd");

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("new@example.com", savedUser.getEmail());
        assertEquals("hashed-new-pwd", savedUser.getPasswordHash());
        assertEquals("New User", savedUser.getFullName());
        assertTrue(savedUser.getRoles().contains(testRole));
    }
}
