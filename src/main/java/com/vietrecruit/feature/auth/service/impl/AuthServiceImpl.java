package com.vietrecruit.feature.auth.service.impl;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.enums.EmailSenderAlias;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.security.AuthCacheService;
import com.vietrecruit.common.security.JwtService;
import com.vietrecruit.feature.auth.dto.request.ChangePasswordRequest;
import com.vietrecruit.feature.auth.dto.request.ForgotPasswordRequest;
import com.vietrecruit.feature.auth.dto.request.LoginRequest;
import com.vietrecruit.feature.auth.dto.request.RegisterByInviteRequest;
import com.vietrecruit.feature.auth.dto.request.RegisterRequest;
import com.vietrecruit.feature.auth.dto.request.ResendOtpRequest;
import com.vietrecruit.feature.auth.dto.request.ResetPasswordRequest;
import com.vietrecruit.feature.auth.dto.request.TokenRefreshRequest;
import com.vietrecruit.feature.auth.dto.request.VerifyOtpRequest;
import com.vietrecruit.feature.auth.dto.response.LoginResponse;
import com.vietrecruit.feature.auth.dto.response.TokenRefreshResponse;
import com.vietrecruit.feature.auth.entity.RefreshToken;
import com.vietrecruit.feature.auth.entity.UserAuthProvider;
import com.vietrecruit.feature.auth.repository.RefreshTokenRepository;
import com.vietrecruit.feature.auth.repository.UserAuthProviderRepository;
import com.vietrecruit.feature.auth.service.AuthService;
import com.vietrecruit.feature.candidate.entity.Candidate;
import com.vietrecruit.feature.candidate.repository.CandidateRepository;
import com.vietrecruit.feature.invitation.entity.Invitation;
import com.vietrecruit.feature.invitation.repository.InvitationRepository;
import com.vietrecruit.feature.notification.dto.EmailRequest;
import com.vietrecruit.feature.notification.service.NotificationService;
import com.vietrecruit.feature.user.entity.Permission;
import com.vietrecruit.feature.user.entity.Role;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.repository.RoleRepository;
import com.vietrecruit.feature.user.repository.UserRepository;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final String ROLE_CANDIDATE = "CANDIDATE";
    private static final String ROLE_COMPANY_ADMIN = "COMPANY_ADMIN";
    private static final String ACCOUNT_TYPE_CANDIDATE = "CANDIDATE";
    private static final String ACCOUNT_TYPE_EMPLOYER = "EMPLOYER";
    private static final Set<String> PLATFORM_ROLES = Set.of("SYSTEM_ADMIN", "CUSTOMER_SERVICE");
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 30;
    private static final long OTP_TTL_SECONDS = 600;
    private static final long OTP_COOLDOWN_SECONDS = 60;
    private static final long OTP_LOCKOUT_SECONDS = 1800;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int OTP_BOUND_MIN = 10_000_000;
    private static final int OTP_BOUND_MAX = 100_000_000;
    private static final long RESET_TOKEN_TTL_SECONDS = 900;
    private static final int RESET_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final CandidateRepository candidateRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final InvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthCacheService authCacheService;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.application.frontend-url}")
    private String frontendBaseUrl;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user =
                userRepository
                        .findByEmail(request.getEmail())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new ApiException(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
        }

        if (user.getPasswordHash() == null) {
            throw new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        if (isAccountLocked(user)) {
            throw new ApiException(ApiErrorCode.AUTH_ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        user.setFailedAttempts((short) 0);
        user.setIsLocked(false);
        user.setLockUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return buildLoginResponse(user);
    }

    @Override
    @Transactional
    public Map<String, Object> register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(ApiErrorCode.USER_EMAIL_CONFLICT);
        }

        String accountType = resolveAccountType(request.getAccountType());
        String roleCode =
                ACCOUNT_TYPE_EMPLOYER.equals(accountType) ? ROLE_COMPANY_ADMIN : ROLE_CANDIDATE;

        Role role =
                roleRepository
                        .findByCode(roleCode)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ApiErrorCode.INTERNAL_ERROR,
                                                "Role not found: " + roleCode));

        User user =
                User.builder()
                        .email(request.getEmail())
                        .passwordHash(passwordEncoder.encode(request.getPassword()))
                        .fullName(request.getFullName())
                        .phone(request.getPhone())
                        .emailVerified(false)
                        .build();
        user.getRoles().add(role);

        userRepository.save(user);

        if (ACCOUNT_TYPE_CANDIDATE.equals(accountType)) {
            candidateRepository.save(Candidate.builder().userId(user.getId()).build());
        }

        String otpCode = generateOtp();
        authCacheService.storeOtp(request.getEmail(), otpCode, user.getId(), OTP_TTL_SECONDS);
        authCacheService.setCooldown(request.getEmail(), OTP_COOLDOWN_SECONDS);

        notificationService.send(
                new EmailRequest(
                        List.of(user.getEmail()),
                        EmailSenderAlias.AUTHENTICATION,
                        "Verify Your Email Address",
                        null,
                        "email-verification",
                        Map.of("otpCode", otpCode, "fullName", user.getFullName())));

        return Map.of("accountType", accountType);
    }

    private String resolveAccountType(String accountType) {
        if (accountType == null || accountType.isBlank()) {
            return ACCOUNT_TYPE_CANDIDATE;
        }
        String normalized = accountType.trim().toUpperCase();
        if (ACCOUNT_TYPE_CANDIDATE.equals(normalized) || ACCOUNT_TYPE_EMPLOYER.equals(normalized)) {
            return normalized;
        }
        throw new ApiException(ApiErrorCode.INVALID_ACCOUNT_TYPE);
    }

    @Override
    @Transactional
    public void registerByInvite(RegisterByInviteRequest request) {
        Invitation invitation =
                invitationRepository
                        .findByToken(request.getToken())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.INVITATION_NOT_FOUND));

        if (!"PENDING".equals(invitation.getStatus())) {
            throw new ApiException(ApiErrorCode.INVITATION_ALREADY_ACCEPTED);
        }

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ApiErrorCode.INVITATION_EXPIRED);
        }

        if (userRepository.existsByEmail(invitation.getEmail())) {
            throw new ApiException(ApiErrorCode.USER_EMAIL_CONFLICT);
        }

        Role role =
                roleRepository
                        .findByCode(invitation.getRole())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ApiErrorCode.INTERNAL_ERROR,
                                                "Role not found: " + invitation.getRole()));

        User user =
                User.builder()
                        .email(invitation.getEmail())
                        .passwordHash(passwordEncoder.encode(request.getPassword()))
                        .fullName(request.getFullName())
                        .companyId(invitation.getCompanyId())
                        .emailVerified(true)
                        .emailVerifiedAt(Instant.now())
                        .build();
        user.getRoles().add(role);
        userRepository.save(user);

        invitation.setStatus("ACCEPTED");
        invitationRepository.save(invitation);

        log.info(
                "User registered via invitation: userId={}, role={}, companyId={}",
                user.getId(),
                invitation.getRole(),
                invitation.getCompanyId());
    }

    @Override
    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail();

        if (authCacheService.isLockedOut(email)) {
            throw new ApiException(ApiErrorCode.AUTH_OTP_LOCKED);
        }

        AuthCacheService.OtpContext context = authCacheService.getOtpContext(email);
        if (context == null) {
            throw new ApiException(ApiErrorCode.AUTH_OTP_EXPIRED);
        }

        if (!context.code().equals(request.getCode())) {
            int attempts = authCacheService.incrementAttempts(email);
            if (attempts >= MAX_OTP_ATTEMPTS) {
                authCacheService.deleteOtp(email);
                authCacheService.setLockout(email, OTP_LOCKOUT_SECONDS);
                throw new ApiException(ApiErrorCode.AUTH_OTP_LOCKED);
            }
            throw new ApiException(ApiErrorCode.AUTH_OTP_INVALID);
        }

        User user =
                userRepository
                        .findById(context.userId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_OTP_EXPIRED));

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        authCacheService.deleteOtp(email);
    }

    @Override
    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        String email = request.getEmail();

        if (authCacheService.isOnCooldown(email)) {
            throw new ApiException(ApiErrorCode.AUTH_OTP_COOLDOWN);
        }

        if (authCacheService.isLockedOut(email)) {
            throw new ApiException(ApiErrorCode.AUTH_OTP_LOCKED);
        }

        userRepository
                .findByEmail(email)
                .filter(user -> Boolean.FALSE.equals(user.getEmailVerified()))
                .ifPresent(
                        user -> {
                            String otpCode = generateOtp();
                            authCacheService.storeOtp(
                                    email, otpCode, user.getId(), OTP_TTL_SECONDS);
                            authCacheService.setCooldown(email, OTP_COOLDOWN_SECONDS);

                            notificationService.send(
                                    new EmailRequest(
                                            List.of(user.getEmail()),
                                            EmailSenderAlias.AUTHENTICATION,
                                            "Verify Your Email Address",
                                            null,
                                            "email-verification",
                                            Map.of(
                                                    "otpCode",
                                                    otpCode,
                                                    "fullName",
                                                    user.getFullName())));
                        });
    }

    @Override
    @Transactional
    public LoginResponse processOAuth2Login(
            String provider,
            String email,
            String providerUserId,
            String providerName,
            String providerAvatarUrl,
            Boolean providerEmailVerified) {

        User user =
                userRepository
                        .findByEmail(email)
                        .map(
                                existingUser -> {
                                    userAuthProviderRepository
                                            .findByUserIdAndProvider(existingUser.getId(), provider)
                                            .ifPresentOrElse(
                                                    link -> {
                                                        link.setProviderName(providerName);
                                                        link.setProviderAvatarUrl(
                                                                providerAvatarUrl);
                                                        userAuthProviderRepository.save(link);
                                                    },
                                                    () -> {
                                                        userAuthProviderRepository.save(
                                                                UserAuthProvider.builder()
                                                                        .userId(
                                                                                existingUser
                                                                                        .getId())
                                                                        .provider(provider)
                                                                        .providerUserId(
                                                                                providerUserId)
                                                                        .providerEmail(email)
                                                                        .providerName(providerName)
                                                                        .providerAvatarUrl(
                                                                                providerAvatarUrl)
                                                                        .build());
                                                    });

                                    if (Boolean.FALSE.equals(existingUser.getEmailVerified())
                                            && Boolean.TRUE.equals(providerEmailVerified)) {
                                        existingUser.setEmailVerified(true);
                                        existingUser.setEmailVerifiedAt(Instant.now());
                                    }
                                    existingUser.setLastLoginAt(Instant.now());
                                    return userRepository.save(existingUser);
                                })
                        .orElseGet(
                                () -> {
                                    Role defaultRole =
                                            roleRepository
                                                    .findByCode(ROLE_CANDIDATE)
                                                    .orElseThrow(
                                                            () ->
                                                                    new ApiException(
                                                                            ApiErrorCode
                                                                                    .INTERNAL_ERROR,
                                                                            "Default role not"
                                                                                    + " found"));

                                    boolean verified = Boolean.TRUE.equals(providerEmailVerified);

                                    User newUser =
                                            User.builder()
                                                    .email(email)
                                                    .fullName(
                                                            providerName != null
                                                                    ? providerName
                                                                    : email.split("@")[0])
                                                    .emailVerified(verified)
                                                    .emailVerifiedAt(
                                                            verified ? Instant.now() : null)
                                                    .lastLoginAt(Instant.now())
                                                    .build();
                                    newUser.getRoles().add(defaultRole);
                                    User savedUser = userRepository.save(newUser);

                                    candidateRepository.save(
                                            Candidate.builder().userId(savedUser.getId()).build());

                                    userAuthProviderRepository.save(
                                            UserAuthProvider.builder()
                                                    .userId(savedUser.getId())
                                                    .provider(provider)
                                                    .providerUserId(providerUserId)
                                                    .providerEmail(email)
                                                    .providerName(providerName)
                                                    .providerAvatarUrl(providerAvatarUrl)
                                                    .build());

                                    return savedUser;
                                });

        return buildLoginResponse(user);
    }

    @Override
    @Transactional
    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        String tokenHash = sha256(request.getRefreshToken());

        RefreshToken storedToken =
                refreshTokenRepository
                        .findByTokenHashAndRevokedFalse(tokenHash)
                        .orElseThrow(
                                () -> new ApiException(ApiErrorCode.AUTH_REFRESH_TOKEN_INVALID));

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new ApiException(ApiErrorCode.AUTH_REFRESH_TOKEN_EXPIRED);
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user =
                userRepository
                        .findByIdWithRolesAndPermissions(storedToken.getUserId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS));

        Set<String> roleCodes =
                user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());

        Set<String> permissionCodes =
                user.getRoles().stream()
                        .flatMap(role -> role.getPermissions().stream())
                        .map(Permission::getCode)
                        .collect(Collectors.toSet());

        authCacheService.cachePermissions(user.getId(), permissionCodes);

        String newAccessToken =
                jwtService.generateAccessToken(
                        user.getId(), roleCodes, Boolean.TRUE.equals(user.getEmailVerified()));
        String newRawRefreshToken = jwtService.generateRefreshToken();

        RefreshToken newRefreshToken =
                RefreshToken.builder()
                        .userId(user.getId())
                        .tokenHash(sha256(newRawRefreshToken))
                        .expiresAt(
                                Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()))
                        .build();
        refreshTokenRepository.save(newRefreshToken);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .build();
    }

    @Override
    @Transactional
    public void logout(String accessToken) {
        try {
            Claims claims = jwtService.parseAndValidate(accessToken);
            String jti = jwtService.extractJti(claims);
            long remainingTtlMs = jwtService.getRemainingTtlMs(claims);
            java.util.UUID userId = jwtService.extractUserId(claims);

            authCacheService.blacklistToken(jti, remainingTtlMs / 1000);

            refreshTokenRepository.revokeAllByUserId(userId);

            authCacheService.evictUser(userId);
        } catch (Exception e) {
            log.debug("Error during logout token processing: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository
                .findByEmail(request.getEmail())
                .ifPresent(
                        user -> {
                            log.info("Password reset requested for user: {}", user.getId());

                            String rawToken = generateResetToken();
                            authCacheService.storeResetToken(
                                    request.getEmail(),
                                    sha256(rawToken),
                                    user.getId(),
                                    RESET_TOKEN_TTL_SECONDS);
                            authCacheService.setCooldown(request.getEmail(), OTP_COOLDOWN_SECONDS);

                            String resetLink =
                                    String.format(
                                            "%s/reset-password?token=%s&email=%s",
                                            frontendBaseUrl, rawToken, request.getEmail());

                            notificationService.send(
                                    new EmailRequest(
                                            List.of(user.getEmail()),
                                            EmailSenderAlias.AUTHENTICATION,
                                            "Password Reset Request",
                                            null,
                                            "forgot-password",
                                            Map.of("resetLink", resetLink)));
                        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail();

        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_RESET_TOKEN_INVALID));

        AuthCacheService.ResetTokenContext context = authCacheService.getResetTokenContext(email);
        if (context == null) {
            throw new ApiException(ApiErrorCode.AUTH_RESET_TOKEN_INVALID);
        }

        String incomingHash = sha256(request.getToken());
        if (!incomingHash.equals(context.tokenHash())) {
            throw new ApiException(ApiErrorCode.AUTH_RESET_TOKEN_INVALID);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        authCacheService.deleteResetToken(email);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        authCacheService.evictUser(user.getId());

        log.info("Password reset completed for user: {}. All sessions revoked.", user.getId());
    }

    @Override
    @Transactional
    public void changePassword(java.util.UUID userId, ChangePasswordRequest request) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ApiException(ApiErrorCode.AUTH_PASSWORD_MISMATCH);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(userId);
        authCacheService.evictUser(userId);

        log.info("Password changed for user: {}. All sessions revoked.", userId);
    }

    private LoginResponse buildLoginResponse(User user) {
        User userWithRoles =
                userRepository
                        .findByIdWithRolesAndPermissions(user.getId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS));

        Set<String> roleCodes =
                userWithRoles.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());

        Set<String> permissionCodes =
                userWithRoles.getRoles().stream()
                        .flatMap(role -> role.getPermissions().stream())
                        .map(Permission::getCode)
                        .collect(Collectors.toSet());

        authCacheService.cachePermissions(user.getId(), permissionCodes);

        String accessToken =
                jwtService.generateAccessToken(
                        user.getId(), roleCodes, Boolean.TRUE.equals(user.getEmailVerified()));
        String rawRefreshToken = jwtService.generateRefreshToken();

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .userId(user.getId())
                        .tokenHash(sha256(rawRefreshToken))
                        .expiresAt(
                                Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()))
                        .build();
        refreshTokenRepository.save(refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .build();
    }

    private boolean isAccountLocked(User user) {
        if (Boolean.TRUE.equals(user.getIsLocked())) {
            if (user.getLockUntil() != null && user.getLockUntil().isAfter(Instant.now())) {
                return true;
            }
            user.setIsLocked(false);
            user.setLockUntil(null);
            user.setFailedAttempts((short) 0);
        }
        return false;
    }

    private void handleFailedLogin(User user) {
        short attempts = (short) (user.getFailedAttempts() + 1);
        user.setFailedAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setIsLocked(true);
            user.setLockUntil(Instant.now().plusSeconds(LOCK_DURATION_MINUTES * 60));
            log.warn("Account locked due to {} failed attempts: userId={}", attempts, user.getId());
        }

        userRepository.save(user);
    }

    private String generateOtp() {
        int code = secureRandom.nextInt(OTP_BOUND_MIN, OTP_BOUND_MAX);
        return String.valueOf(code);
    }

    /**
     * Generates a cryptographically secure reset token (32 bytes = 256 bits, hex-encoded). Used
     * exclusively for password reset links. Email verification OTP uses the separate {@link
     * #generateOtp()} method which produces a short numeric code suitable for on-screen entry with
     * rate-limited retries.
     */
    private String generateResetToken() {
        byte[] bytes = new byte[RESET_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
