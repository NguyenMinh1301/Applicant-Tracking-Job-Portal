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

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.security.AuthCacheService;
import com.vietrecruit.common.security.JwtService;
import com.vietrecruit.feature.auth.dto.request.ChangePasswordRequest;
import com.vietrecruit.feature.auth.dto.request.ForgotPasswordRequest;
import com.vietrecruit.feature.auth.dto.request.LoginRequest;
import com.vietrecruit.feature.auth.dto.request.RegisterRequest;
import com.vietrecruit.feature.auth.dto.request.ResendOtpRequest;
import com.vietrecruit.feature.auth.dto.request.ResetPasswordRequest;
import com.vietrecruit.feature.auth.dto.request.VerifyOtpRequest;
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

    // ── Login Tests ─────────────────────────────────────────────────────

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

    // ── Register Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("Should register new user and send verification OTP")
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
                .storeOtp(eq("new@example.com"), anyString(), any(java.util.UUID.class), eq(600L));
        verify(authCacheService, times(1)).setCooldown(eq("new@example.com"), eq(60L));

        ArgumentCaptor<EmailRequest> emailCaptor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(notificationService, times(1)).send(emailCaptor.capture());
        assertEquals("email-verification", emailCaptor.getValue().templateId());
    }

    // ── Verify OTP Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("Should verify OTP successfully")
    void verifyOtp_ValidCode_SetsVerified() {
        testUser.setEmailVerified(false);
        VerifyOtpRequest request = new VerifyOtpRequest("test@example.com", "12345678");

        when(authCacheService.isLockedOut("test@example.com")).thenReturn(false);
        when(authCacheService.getOtpContext("test@example.com"))
                .thenReturn(new AuthCacheService.OtpContext("12345678", testUser.getId(), 0));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        authService.verifyOtp(request);

        assertTrue(testUser.getEmailVerified());
        assertNotNull(testUser.getEmailVerifiedAt());
        verify(userRepository, times(1)).save(testUser);
        verify(authCacheService, times(1)).deleteOtp("test@example.com");
    }

    @Test
    @DisplayName("Should throw AUTH_OTP_LOCKED when locked out")
    void verifyOtp_LockedOut_ThrowsOtpLocked() {
        VerifyOtpRequest request = new VerifyOtpRequest("test@example.com", "12345678");

        when(authCacheService.isLockedOut("test@example.com")).thenReturn(true);

        ApiException exception =
                assertThrows(ApiException.class, () -> authService.verifyOtp(request));
        assertEquals(ApiErrorCode.AUTH_OTP_LOCKED, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw AUTH_OTP_EXPIRED when no OTP context found")
    void verifyOtp_ExpiredOtp_ThrowsOtpExpired() {
        VerifyOtpRequest request = new VerifyOtpRequest("test@example.com", "12345678");

        when(authCacheService.isLockedOut("test@example.com")).thenReturn(false);
        when(authCacheService.getOtpContext("test@example.com")).thenReturn(null);

        ApiException exception =
                assertThrows(ApiException.class, () -> authService.verifyOtp(request));
        assertEquals(ApiErrorCode.AUTH_OTP_EXPIRED, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw AUTH_OTP_INVALID and increment attempts on wrong code")
    void verifyOtp_WrongCode_IncrementsAttempts() {
        VerifyOtpRequest request = new VerifyOtpRequest("test@example.com", "99999999");

        when(authCacheService.isLockedOut("test@example.com")).thenReturn(false);
        when(authCacheService.getOtpContext("test@example.com"))
                .thenReturn(new AuthCacheService.OtpContext("12345678", testUser.getId(), 0));
        when(authCacheService.incrementAttempts("test@example.com")).thenReturn(1);

        ApiException exception =
                assertThrows(ApiException.class, () -> authService.verifyOtp(request));
        assertEquals(ApiErrorCode.AUTH_OTP_INVALID, exception.getErrorCode());
        verify(authCacheService, times(1)).incrementAttempts("test@example.com");
    }

    @Test
    @DisplayName("Should lock out and throw AUTH_OTP_LOCKED after max attempts")
    void verifyOtp_MaxAttempts_LocksAndThrowsOtpLocked() {
        VerifyOtpRequest request = new VerifyOtpRequest("test@example.com", "99999999");

        when(authCacheService.isLockedOut("test@example.com")).thenReturn(false);
        when(authCacheService.getOtpContext("test@example.com"))
                .thenReturn(new AuthCacheService.OtpContext("12345678", testUser.getId(), 4));
        when(authCacheService.incrementAttempts("test@example.com")).thenReturn(5);

        ApiException exception =
                assertThrows(ApiException.class, () -> authService.verifyOtp(request));
        assertEquals(ApiErrorCode.AUTH_OTP_LOCKED, exception.getErrorCode());
        verify(authCacheService, times(1)).deleteOtp("test@example.com");
        verify(authCacheService, times(1)).setLockout("test@example.com", 1800L);
    }

    // ── Resend OTP Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("Should resend OTP for unverified user")
    void resendOtp_UnverifiedUser_SendsOtp() {
        testUser.setEmailVerified(false);
        ResendOtpRequest request = new ResendOtpRequest("test@example.com");

        when(authCacheService.isOnCooldown("test@example.com")).thenReturn(false);
        when(authCacheService.isLockedOut("test@example.com")).thenReturn(false);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));

        authService.resendOtp(request);

        verify(authCacheService, times(1))
                .storeOtp(eq("test@example.com"), anyString(), eq(testUser.getId()), eq(600L));
        verify(authCacheService, times(1)).setCooldown("test@example.com", 60L);
        verify(notificationService, times(1)).send(any(EmailRequest.class));
    }

    @Test
    @DisplayName("Should silently succeed for already verified user resend")
    void resendOtp_AlreadyVerified_SilentSuccess() {
        testUser.setEmailVerified(true);
        ResendOtpRequest request = new ResendOtpRequest("test@example.com");

        when(authCacheService.isOnCooldown("test@example.com")).thenReturn(false);
        when(authCacheService.isLockedOut("test@example.com")).thenReturn(false);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(testUser));

        authService.resendOtp(request);

        verify(authCacheService, never())
                .storeOtp(anyString(), anyString(), any(java.util.UUID.class), anyLong());
        verify(notificationService, never()).send(any(EmailRequest.class));
    }

    @Test
    @DisplayName("Should throw AUTH_OTP_COOLDOWN when on cooldown")
    void resendOtp_OnCooldown_ThrowsOtpCooldown() {
        ResendOtpRequest request = new ResendOtpRequest("test@example.com");

        when(authCacheService.isOnCooldown("test@example.com")).thenReturn(true);

        ApiException exception =
                assertThrows(ApiException.class, () -> authService.resendOtp(request));
        assertEquals(ApiErrorCode.AUTH_OTP_COOLDOWN, exception.getErrorCode());
    }

    @Test
    @DisplayName("Should throw AUTH_OTP_LOCKED when locked out on resend")
    void resendOtp_LockedOut_ThrowsOtpLocked() {
        ResendOtpRequest request = new ResendOtpRequest("test@example.com");

        when(authCacheService.isOnCooldown("test@example.com")).thenReturn(false);
        when(authCacheService.isLockedOut("test@example.com")).thenReturn(true);

        ApiException exception =
                assertThrows(ApiException.class, () -> authService.resendOtp(request));
        assertEquals(ApiErrorCode.AUTH_OTP_LOCKED, exception.getErrorCode());
    }

    // ── OAuth2 Provider Claim Gating Tests ──────────────────────────────

    @Test
    @DisplayName("Should auto-verify email when Google provider confirms email_verified")
    void processOAuth2Login_GoogleVerified_AutoVerifies() {
        testUser.setEmailVerified(false);
        testUser.setEmailVerifiedAt(null);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(userAuthProviderRepository.findByUserIdAndProvider(testUser.getId(), "GOOGLE"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userRepository.findByIdWithRolesAndPermissions(testUser.getId()))
                .thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(eq(testUser.getId()), anySet(), anyBoolean()))
                .thenReturn("access.token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh.token");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        authService.processOAuth2Login(
                "GOOGLE", "test@example.com", "google-id", "Test", "avatar-url", true);

        assertTrue(testUser.getEmailVerified());
        assertNotNull(testUser.getEmailVerifiedAt());
    }

    @Test
    @DisplayName("Should NOT auto-verify email for GitHub provider")
    void processOAuth2Login_GitHubUnverified_NoAutoVerify() {
        testUser.setEmailVerified(false);
        testUser.setEmailVerifiedAt(null);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(userAuthProviderRepository.findByUserIdAndProvider(testUser.getId(), "GITHUB"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userRepository.findByIdWithRolesAndPermissions(testUser.getId()))
                .thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(eq(testUser.getId()), anySet(), anyBoolean()))
                .thenReturn("access.token");
        when(jwtService.generateRefreshToken()).thenReturn("refresh.token");
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(900000L);

        authService.processOAuth2Login(
                "GITHUB", "test@example.com", "github-id", "Test", "avatar-url", false);

        assertFalse(testUser.getEmailVerified());
        assertNull(testUser.getEmailVerifiedAt());
    }

    // ── Change Password Tests ───────────────────────────────────────────

    @Test
    @DisplayName("Should change password, revoke tokens, and evict cache")
    void changePassword_Success() {
        ChangePasswordRequest request =
                ChangePasswordRequest.builder()
                        .currentPassword("oldPassword123")
                        .newPassword("newPassword456")
                        .build();

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("oldPassword123", testUser.getPasswordHash()))
                .thenReturn(true);
        when(passwordEncoder.encode("newPassword456")).thenReturn("hashed-new-pwd");

        authService.changePassword(testUser.getId(), request);

        assertEquals("hashed-new-pwd", testUser.getPasswordHash());
        verify(userRepository, times(1)).save(testUser);
        verify(refreshTokenRepository, times(1)).revokeAllByUserId(testUser.getId());
        verify(authCacheService, times(1)).evictUser(testUser.getId());
    }

    @Test
    @DisplayName("Should throw AUTH_PASSWORD_MISMATCH when current password is wrong")
    void changePassword_WrongCurrentPassword_ThrowsMismatch() {
        ChangePasswordRequest request =
                ChangePasswordRequest.builder()
                        .currentPassword("wrongPassword")
                        .newPassword("newPassword456")
                        .build();

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", testUser.getPasswordHash()))
                .thenReturn(false);

        ApiException exception =
                assertThrows(
                        ApiException.class,
                        () -> authService.changePassword(testUser.getId(), request));
        assertEquals(ApiErrorCode.AUTH_PASSWORD_MISMATCH, exception.getErrorCode());

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    @Test
    @DisplayName("Should throw AUTH_PASSWORD_MISMATCH for OAuth-only user with null password hash")
    void changePassword_NullPasswordHash_ThrowsMismatch() {
        testUser.setPasswordHash(null);
        ChangePasswordRequest request =
                ChangePasswordRequest.builder()
                        .currentPassword("anyPassword")
                        .newPassword("newPassword456")
                        .build();

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        ApiException exception =
                assertThrows(
                        ApiException.class,
                        () -> authService.changePassword(testUser.getId(), request));
        assertEquals(ApiErrorCode.AUTH_PASSWORD_MISMATCH, exception.getErrorCode());
    }

    // ── Forgot Password Tests ───────────────────────────────────────────

    @Test
    @DisplayName("Should generate high-entropy token and send email with secure reset link")
    void forgotPassword_ExistingUser_SendsEmailWithSecureToken() {
        ForgotPasswordRequest request =
                ForgotPasswordRequest.builder().email("test@example.com").build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        authService.forgotPassword(request);

        // Verify reset token stored (not OTP)
        verify(authCacheService, times(1))
                .storeResetToken(
                        eq("test@example.com"), anyString(), eq(testUser.getId()), eq(900L));
        verify(authCacheService, times(1)).setCooldown(eq("test@example.com"), eq(60L));

        // Verify OTP storage was NOT used
        verify(authCacheService, never())
                .storeOtp(anyString(), anyString(), any(java.util.UUID.class), anyLong());

        ArgumentCaptor<EmailRequest> emailCaptor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(notificationService, times(1)).send(emailCaptor.capture());

        EmailRequest sentEmail = emailCaptor.getValue();
        assertEquals("forgot-password", sentEmail.templateId());

        java.util.Map<String, Object> model = sentEmail.templateVariables();
        String resetLink = (String) model.get("resetLink");
        assertNotNull(resetLink);
        assertFalse(resetLink.contains("placeholder"));
        assertTrue(resetLink.contains("/reset-password?token="));
        assertTrue(resetLink.contains("&email=test@example.com"));

        // Extract token from link and verify it's 64 hex chars (32 bytes)
        String token =
                resetLink.substring(resetLink.indexOf("token=") + 6, resetLink.indexOf("&email"));
        assertEquals(64, token.length(), "Reset token must be 64 hex characters (32 bytes)");
        assertTrue(token.matches("[0-9a-f]{64}"), "Reset token must be lowercase hex");
    }

    // ── Reset Password Tests ───────────────────────────────────────────

    @Test
    @DisplayName("Should reset password, delete token, revoke tokens, and evict cache")
    void resetPassword_Success() {
        String rawToken = "a".repeat(64);
        String tokenHash = sha256Helper(rawToken);

        ResetPasswordRequest request =
                ResetPasswordRequest.builder()
                        .email("test@example.com")
                        .token(rawToken)
                        .newPassword("newSecurePassword123")
                        .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(authCacheService.getResetTokenContext("test@example.com"))
                .thenReturn(new AuthCacheService.ResetTokenContext(tokenHash, testUser.getId()));
        when(passwordEncoder.encode("newSecurePassword123")).thenReturn("hashed-new-pwd");

        authService.resetPassword(request);

        assertEquals("hashed-new-pwd", testUser.getPasswordHash());
        verify(userRepository, times(1)).save(testUser);
        verify(authCacheService, times(1)).deleteResetToken("test@example.com");
        verify(refreshTokenRepository, times(1)).revokeAllByUserId(testUser.getId());
        verify(authCacheService, times(1)).evictUser(testUser.getId());
    }

    @Test
    @DisplayName("Should throw AUTH_RESET_TOKEN_INVALID when token expired (no context in Redis)")
    void resetPassword_ExpiredToken_Throws() {
        ResetPasswordRequest request =
                ResetPasswordRequest.builder()
                        .email("test@example.com")
                        .token("sometoken")
                        .newPassword("newPassword123")
                        .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(authCacheService.getResetTokenContext("test@example.com")).thenReturn(null);

        ApiException exception =
                assertThrows(ApiException.class, () -> authService.resetPassword(request));
        assertEquals(ApiErrorCode.AUTH_RESET_TOKEN_INVALID, exception.getErrorCode());

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw AUTH_RESET_TOKEN_INVALID when token hash does not match")
    void resetPassword_InvalidToken_Throws() {
        ResetPasswordRequest request =
                ResetPasswordRequest.builder()
                        .email("test@example.com")
                        .token("wrongtoken")
                        .newPassword("newPassword123")
                        .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(authCacheService.getResetTokenContext("test@example.com"))
                .thenReturn(
                        new AuthCacheService.ResetTokenContext(
                                "correct-hash-value", testUser.getId()));

        ApiException exception =
                assertThrows(ApiException.class, () -> authService.resetPassword(request));
        assertEquals(ApiErrorCode.AUTH_RESET_TOKEN_INVALID, exception.getErrorCode());

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName(
            "Should throw AUTH_RESET_TOKEN_INVALID when user not found — no information leakage")
    void resetPassword_UserNotFound_ThrowsGenericError() {
        ResetPasswordRequest request =
                ResetPasswordRequest.builder()
                        .email("nonexistent@example.com")
                        .token("sometoken")
                        .newPassword("newPassword123")
                        .build();

        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        ApiException exception =
                assertThrows(ApiException.class, () -> authService.resetPassword(request));
        assertEquals(ApiErrorCode.AUTH_RESET_TOKEN_INVALID, exception.getErrorCode());
    }

    // ── Test Helpers ───────────────────────────────────────────────────

    private static String sha256Helper(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
