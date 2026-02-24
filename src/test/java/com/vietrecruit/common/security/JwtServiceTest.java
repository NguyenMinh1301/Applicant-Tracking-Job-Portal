package com.vietrecruit.common.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret =
            "thisisaverysecuresecretkeythatisatleast64byteslongforhs512algorithmvalidation";
    private final long accessTokenExpirationMs = 900000;
    private final long refreshTokenExpirationMs = 604800000;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, accessTokenExpirationMs, refreshTokenExpirationMs);
    }

    @Test
    @DisplayName("Should generate valid access token")
    void shouldGenerateAccessToken() {
        // Arrange
        UUID userId = UUID.randomUUID();
        Set<String> roles = Set.of("CANDIDATE", "USER:MANAGE");

        // Act
        String token = jwtService.generateAccessToken(userId, roles, true);

        // Assert
        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));

        Claims claims = jwtService.parseAndValidate(token);
        assertEquals(userId, jwtService.extractUserId(claims));
        assertNotNull(jwtService.extractJti(claims));
        Set<String> extractedRoles = jwtService.extractRoles(claims);
        assertEquals(2, extractedRoles.size());
        assertTrue(extractedRoles.containsAll(roles));
    }

    @Test
    @DisplayName("Should generate random refresh token")
    void shouldGenerateRefreshToken() {
        String token1 = jwtService.generateRefreshToken();
        String token2 = jwtService.generateRefreshToken();

        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("Should validate token successfully")
    void shouldValidateToken() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), Set.of("CANDIDATE"), true);
        assertTrue(jwtService.isTokenValid(token));
        assertDoesNotThrow(() -> jwtService.parseAndValidate(token));
    }

    @Test
    @DisplayName("Should reject invalid token")
    void shouldRejectInvalidToken() {
        String invalidToken = "invalid.jwt.token";
        assertFalse(jwtService.isTokenValid(invalidToken));
        assertThrows(JwtException.class, () -> jwtService.parseAndValidate(invalidToken));
    }

    @Test
    @DisplayName("Should correctly identify expired token")
    void shouldIdentifyExpiredToken() throws InterruptedException {
        // Create a JWT service with 1ms expiration
        JwtService shortLivedJwtService = new JwtService(secret, 1, refreshTokenExpirationMs);
        String token =
                shortLivedJwtService.generateAccessToken(
                        UUID.randomUUID(), Set.of("CANDIDATE"), true);

        // Wait for token to expire
        Thread.sleep(10);

        assertFalse(shortLivedJwtService.isTokenValid(token));
        assertThrows(JwtException.class, () -> shortLivedJwtService.parseAndValidate(token));
    }

    @Test
    @DisplayName("Should return remaining TTL correctly")
    void shouldReturnRemainingTtl() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), Set.of("CANDIDATE"), true);
        Claims claims = jwtService.parseAndValidate(token);

        long ttl = jwtService.getRemainingTtlMs(claims);
        assertTrue(ttl > 0 && ttl <= accessTokenExpirationMs);
    }
}
