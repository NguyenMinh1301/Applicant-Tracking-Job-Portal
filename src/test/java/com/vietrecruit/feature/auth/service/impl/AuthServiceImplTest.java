package com.vietrecruit.feature.auth.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
import com.vietrecruit.feature.auth.dto.request.ResendVerificationRequest;
import com.vietrecruit.feature.auth.dto.response.LoginResponse;
import com.vietrecruit.feature.auth.entity.RefreshToken;
import com.vietrecruit.feature.auth.repository.RefreshTokenRepository;
import com.vietrecruit.feature.auth.repository.UserAuthProviderRepository;
import com.vietrecruit.feature.notification.dto.EmailRequest;
import com.vietrecruit.feature.notification.service.NotificationService;
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

    @Mock private UserAuthProviderRepository userAuthProviderRepository;

    @Mock private PasswordEncoder passwordEncoder;

    @Mock private JwtService jwtService;

    @Mock private AuthCacheService authCacheService;

    @Mock private NotificationService notificationService;

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
                        .fullName("Test User")
                        .isActive(true)
                        .isLocked(false)
                        .failedAttempts((short) 0)
                        .emailVerified(true)
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

        when(jwtService.generateAccessToken(eq(testUser.getId()), anySet(), anyBoolean()))
                .thenReturn("access.token.here");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access.token.here", response.getAccessToken());
        assertEquals("raw-refresh-token", response.getRefreshToken());
        assertEquals(900L, response.getExpiresIn());

        verify(userRepository, times(1)).save(testUser);
        verify(authCacheService, times(1)).cachePermissions(eq(testUser.getId()), anySet());
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));

        assertEquals(0, testUser.getFailedAttempts().intValue());
        assertFalse(testUser.getIsLocked());
    }

    @Test
    @DisplayName(
            "Should throw AUTH_INVALID_CREDENTIALS when password mismatch and increment failed"
                    + " attempts")
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
    @DisplayName("Should register new user and send verification email")
    void register_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setFullName("New User");

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByCode("CANDIDATE")).thenReturn(Optional.of(testRole));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-new-pwd");
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        invocation -> {
                            User u = invocation.getArgument(0);
                            if (u.getId() == null) {
                                u.setId(UUID.randomUUID());
                            }
                            return u;
                        });

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("new@example.com", savedUser.getEmail());
        assertEquals("hashed-new-pwd", savedUser.getPasswordHash());
        assertEquals("New User", savedUser.getFullName());
        assertFalse(savedUser.getEmailVerified());
        assertTrue(savedUser.getRoles().contains(testRole));

        verify(authCacheService, times(1))
                .storeVerificationToken(anyString(), any(UUID.class), eq(900L));

        ArgumentCaptor<EmailRequest> emailCaptor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(notificationService, times(1)).send(emailCaptor.capture());
        assertEquals("email-verification", emailCaptor.getValue().templateId());
    }

    @Test
    @DisplayName("Should throw AUTH_INVALID_CREDENTIALS for OAuth2-only user with null password")
    void login_OAuthOnlyUser_NoPassword_ThrowsInvalidCredentials() {
        testUser.setPasswordHash(null);

        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));

        ApiException exception = assertThrows(ApiException.class, () -> authService.login(request));
        assertEquals(ApiErrorCode.AUTH_INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should verify email with valid token")
    void verifyEmail_ValidToken_SetsVerified() {
        testUser.setEmailVerified(false);
        String rawToken = "test-token-uuid";

        when(authCacheService.getVerificationToken(anyString())).thenReturn(testUser.getId());
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        authService.verifyEmail(rawToken);

        assertTrue(testUser.getEmailVerified());
        assertNotNull(testUser.getEmailVerifiedAt());
        verify(userRepository, times(1)).save(testUser);
        verify(authCacheService, times(1)).deleteVerificationToken(anyString());
    }

    @Test
    @DisplayName("Should throw AUTH_VERIFY_TOKEN_INVALID for invalid token")
    void verifyEmail_InvalidToken_ThrowsException() {
        when(authCacheService.getVerificationToken(anyString())).thenReturn(null);

        ApiException exception =
                assertThrows(ApiException.class, () -> authService.verifyEmail("invalid-token"));
        assertEquals(ApiErrorCode.AUTH_VERIFY_TOKEN_INVALID, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should resend verification for unverified user")
    void resendVerification_UnverifiedUser_SendsEmail() {
        testUser.setEmailVerified(false);
        ResendVerificationRequest request = new ResendVerificationRequest("test@example.com");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));

        authService.resendVerification(request);

        verify(authCacheService, times(1))
                .storeVerificationToken(anyString(), eq(testUser.getId()), eq(900L));
        verify(notificationService, times(1)).send(any(EmailRequest.class));
    }

    @Test
    @DisplayName("Should silently succeed for already verified user resend")
    void resendVerification_AlreadyVerified_SilentSuccess() {
        testUser.setEmailVerified(true);
        ResendVerificationRequest request = new ResendVerificationRequest("test@example.com");

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));

        authService.resendVerification(request);

        verify(authCacheService, never())
                .storeVerificationToken(anyString(), any(UUID.class), anyLong());
        verify(notificationService, never()).send(any(EmailRequest.class));
    }
}
