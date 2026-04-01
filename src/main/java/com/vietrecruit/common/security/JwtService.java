package com.vietrecruit.common.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/** Handles JWT generation, parsing, and claim extraction using HMAC-SHA512. */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final String issuer;
    private final String audience;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration:900000}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpirationMs,
            @Value("${jwt.issuer:vietrecruit}") String issuer,
            @Value("${jwt.audience:vietrecruit-api}") String audience) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.issuer = issuer;
        this.audience = audience;
    }

    /**
     * Generates a signed JWT access token embedding the user ID, roles, and email verification
     * status.
     *
     * @param userId the authenticated user's UUID
     * @param roleCodes set of role code strings to embed as the {@code roles} claim
     * @param emailVerified whether the user's email is verified
     * @return signed compact JWT string
     */
    public String generateAccessToken(UUID userId, Set<String> roleCodes, boolean emailVerified) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(issuer)
                .audience()
                .add(audience)
                .and()
                .claim("roles", roleCodes)
                .claim("email_verified", emailVerified)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Generates a random UUID string to be stored as a refresh token.
     *
     * @return opaque refresh token string
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Parses and fully validates the JWT: signature, issuer, audience, and expiry.
     *
     * @param token compact JWT string
     * @return validated {@link Claims}
     * @throws io.jsonwebtoken.JwtException if the token is invalid, expired, or tampered
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Returns true if the token passes full validation; false on any JWT exception.
     *
     * @param token compact JWT string
     * @return {@code true} if valid, {@code false} otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            parseAndValidate(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.debug("JWT invalid: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Extracts the user UUID from the JWT subject claim.
     *
     * @param claims parsed JWT claims
     * @return user UUID
     */
    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extracts the JWT token ID (jti) claim used for blacklisting.
     *
     * @param claims parsed JWT claims
     * @return JWT ID string
     */
    public String extractJti(Claims claims) {
        return claims.getId();
    }

    /**
     * Extracts the {@code roles} claim as a set of role code strings.
     *
     * @param claims parsed JWT claims
     * @return set of role codes, empty if the claim is absent or malformed
     */
    @SuppressWarnings("unchecked")
    public Set<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof java.util.Collection<?> collection) {
            return new java.util.HashSet<>(collection.stream().map(Object::toString).toList());
        }
        return Set.of();
    }

    /**
     * Extracts the {@code email_verified} boolean claim.
     *
     * @param claims parsed JWT claims
     * @return {@code true} if the claim is present and set to {@code true}
     */
    public boolean extractEmailVerified(Claims claims) {
        Boolean verified = claims.get("email_verified", Boolean.class);
        return Boolean.TRUE.equals(verified);
    }

    /**
     * Returns the configured access token TTL in milliseconds.
     *
     * @return access token expiration in milliseconds
     */
    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    /**
     * Returns the configured refresh token TTL in milliseconds.
     *
     * @return refresh token expiration in milliseconds
     */
    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    /**
     * Returns the remaining time-to-live of the token in milliseconds, clamped to zero if already
     * expired.
     *
     * @param claims parsed JWT claims
     * @return remaining TTL in milliseconds, never negative
     */
    public long getRemainingTtlMs(Claims claims) {
        Date expiration = claims.getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }
}
