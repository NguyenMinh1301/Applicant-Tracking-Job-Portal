package com.vietrecruit.feature.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.security.AuthCacheService;
import com.vietrecruit.common.security.JwtService;
import com.vietrecruit.feature.auth.dto.request.ForgotPasswordRequest;
import com.vietrecruit.feature.auth.dto.request.LoginRequest;
import com.vietrecruit.feature.auth.dto.request.RegisterRequest;
import com.vietrecruit.feature.auth.dto.request.ResendVerificationRequest;
import com.vietrecruit.feature.auth.dto.request.TokenRefreshRequest;
import com.vietrecruit.feature.auth.dto.response.LoginResponse;
import com.vietrecruit.feature.auth.dto.response.TokenRefreshResponse;
import com.vietrecruit.feature.auth.entity.RefreshToken;
import com.vietrecruit.feature.auth.entity.UserAuthProvider;
import com.vietrecruit.feature.auth.repository.RefreshTokenRepository;
import com.vietrecruit.feature.auth.repository.UserAuthProviderRepository;
import com.vietrecruit.feature.auth.service.AuthService;
import com.vietrecruit.feature.notification.dto.EmailRequest;
import com.vietrecruit.feature.notification.dto.EmailSenderAlias;
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

    private static final String DEFAULT_ROLE = "CANDIDATE";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 30;
    private static final long VERIFICATION_TOKEN_TTL_SECONDS = 900;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthCacheService authCacheService;
    private final NotificationService notificationService;

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
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException(ApiErrorCode.USER_EMAIL_CONFLICT);
        }

        Role defaultRole =
                roleRepository
                        .findByCode(DEFAULT_ROLE)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                ApiErrorCode.INTERNAL_ERROR,
                                                "Default role not found"));

        User user =
                User.builder()
                        .email(request.getEmail())
                        .passwordHash(passwordEncoder.encode(request.getPassword()))
                        .fullName(request.getFullName())
                        .phone(request.getPhone())
                        .emailVerified(false)
                        .build();
        user.getRoles().add(defaultRole);

        userRepository.save(user);

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);
        authCacheService.storeVerificationToken(
                tokenHash, user.getId(), VERIFICATION_TOKEN_TTL_SECONDS);

        String verifyLink = String.format("%s/verify-email?token=%s", frontendBaseUrl, rawToken);

        notificationService.send(
                new EmailRequest(
                        List.of(user.getEmail()),
                        EmailSenderAlias.AUTHENTICATION,
                        "Verify Your Email Address",
                        null,
                        "email-verification",
                        Map.of("verifyLink", verifyLink, "fullName", user.getFullName())));
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        String tokenHash = sha256(token);
        UUID userId = authCacheService.getVerificationToken(tokenHash);

        if (userId == null) {
            throw new ApiException(ApiErrorCode.AUTH_VERIFY_TOKEN_INVALID);
        }

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new ApiException(ApiErrorCode.AUTH_VERIFY_TOKEN_INVALID));

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);

        authCacheService.deleteVerificationToken(tokenHash);
    }

    @Override
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        userRepository
                .findByEmail(request.getEmail())
                .filter(user -> Boolean.FALSE.equals(user.getEmailVerified()))
                .ifPresent(
                        user -> {
                            String rawToken = UUID.randomUUID().toString();
                            String tokenHash = sha256(rawToken);
                            authCacheService.storeVerificationToken(
                                    tokenHash, user.getId(), VERIFICATION_TOKEN_TTL_SECONDS);

                            String verifyLink =
                                    String.format(
                                            "%s/verify-email?token=%s", frontendBaseUrl, rawToken);

                            notificationService.send(
                                    new EmailRequest(
                                            List.of(user.getEmail()),
                                            EmailSenderAlias.AUTHENTICATION,
                                            "Verify Your Email Address",
                                            null,
                                            "email-verification",
                                            Map.of(
                                                    "verifyLink",
                                                    verifyLink,
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
            String providerAvatarUrl) {

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

                                    if (Boolean.FALSE.equals(existingUser.getEmailVerified())) {
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
                                                    .findByCode(DEFAULT_ROLE)
                                                    .orElseThrow(
                                                            () ->
                                                                    new ApiException(
                                                                            ApiErrorCode
                                                                                    .INTERNAL_ERROR,
                                                                            "Default role not"
                                                                                    + " found"));

                                    User newUser =
                                            User.builder()
                                                    .email(email)
                                                    .fullName(
                                                            providerName != null
                                                                    ? providerName
                                                                    : email.split("@")[0])
                                                    .emailVerified(true)
                                                    .emailVerifiedAt(Instant.now())
                                                    .lastLoginAt(Instant.now())
                                                    .build();
                                    newUser.getRoles().add(defaultRole);
                                    User savedUser = userRepository.save(newUser);

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
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository
                .findByEmail(request.getEmail())
                .ifPresent(
                        user -> {
                            log.info("Password reset requested for user: {}", user.getId());
                            String resetLink =
                                    String.format(
                                            "%s/reset-password?token=placeholder", frontendBaseUrl);

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

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
