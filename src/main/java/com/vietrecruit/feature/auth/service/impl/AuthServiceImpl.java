package com.vietrecruit.feature.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.vietrecruit.feature.auth.dto.request.TokenRefreshRequest;
import com.vietrecruit.feature.auth.dto.response.LoginResponse;
import com.vietrecruit.feature.auth.dto.response.TokenRefreshResponse;
import com.vietrecruit.feature.auth.entity.RefreshToken;
import com.vietrecruit.feature.auth.repository.RefreshTokenRepository;
import com.vietrecruit.feature.auth.service.AuthService;
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

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthCacheService authCacheService;

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

        if (isAccountLocked(user)) {
            throw new ApiException(ApiErrorCode.AUTH_ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        // Reset failed attempts on successful login
        user.setFailedAttempts((short) 0);
        user.setIsLocked(false);
        user.setLockUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Load roles and permissions eagerly
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

        // Cache permissions in Redis
        authCacheService.cachePermissions(user.getId(), permissionCodes);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user.getId(), roleCodes);
        String rawRefreshToken = jwtService.generateRefreshToken();

        // Store hashed refresh token
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
                        .build();
        user.getRoles().add(defaultRole);

        userRepository.save(user);
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

        // Revoke old refresh token (rotation)
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // Load user with roles
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

        // Refresh permissions cache
        authCacheService.cachePermissions(user.getId(), permissionCodes);

        // Generate new token pair
        String newAccessToken = jwtService.generateAccessToken(user.getId(), roleCodes);
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

            // Blacklist access token
            authCacheService.blacklistToken(jti, remainingTtlMs / 1000);

            // Revoke all refresh tokens for user
            refreshTokenRepository.revokeAllByUserId(userId);

            // Evict cache
            authCacheService.evictUser(userId);
        } catch (Exception e) {
            log.debug("Error during logout token processing: {}", e.getMessage());
            // Logout should not fail even if token is invalid
        }
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        // Always return success to prevent email enumeration
        userRepository
                .findByEmail(request.getEmail())
                .ifPresent(
                        user -> {
                            // TODO: Generate password reset token and send email
                            log.info("Password reset requested for user: {}", user.getId());
                        });
    }

    private boolean isAccountLocked(User user) {
        if (Boolean.TRUE.equals(user.getIsLocked())) {
            if (user.getLockUntil() != null && user.getLockUntil().isAfter(Instant.now())) {
                return true;
            }
            // Lock expired, reset
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
