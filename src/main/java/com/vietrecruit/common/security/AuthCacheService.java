package com.vietrecruit.common.security;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthCacheService {

    private static final String PERMISSIONS_KEY_PREFIX = "auth:user:%s:permissions";
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:";
    private static final long PERMISSIONS_TTL_MINUTES = 30;

    private static final String OTP_KEY_PREFIX = "auth:verify:otp:";
    private static final String OTP_COOLDOWN_PREFIX = "auth:verify:cooldown:";
    private static final String OTP_LOCKOUT_PREFIX = "auth:verify:lockout:";
    private static final String RESET_TOKEN_PREFIX = "auth:reset:";
    private static final String RESET_RATE_PREFIX = "ratelimit:password-reset:";
    private static final long RESET_RATE_TTL_SECONDS = 900; // 15 min
    private static final int RESET_RATE_MAX = 3;

    private final StringRedisTemplate redisTemplate;

    // ── Permission Cache ────────────────────────────────────────────────

    public void cachePermissions(UUID userId, Set<String> permissions) {
        String key = String.format(PERMISSIONS_KEY_PREFIX, userId);
        redisTemplate.delete(key);
        if (!permissions.isEmpty()) {
            redisTemplate.opsForSet().add(key, permissions.toArray(new String[0]));
            redisTemplate.expire(key, PERMISSIONS_TTL_MINUTES, TimeUnit.MINUTES);
        }
    }

    public Set<String> getPermissions(UUID userId) {
        String key = String.format(PERMISSIONS_KEY_PREFIX, userId);
        Set<String> members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return null;
        }
        return members;
    }

    public void evictUser(UUID userId) {
        String permissionsKey = String.format(PERMISSIONS_KEY_PREFIX, userId);
        redisTemplate.delete(permissionsKey);
    }

    // ── Token Blacklist ─────────────────────────────────────────────────

    public void blacklistToken(String jti, long remainingTtlSeconds) {
        if (remainingTtlSeconds > 0) {
            String key = BLACKLIST_KEY_PREFIX + jti;
            redisTemplate.opsForValue().set(key, "1", remainingTtlSeconds, TimeUnit.SECONDS);
        }
    }

    public boolean isBlacklisted(String jti) {
        String key = BLACKLIST_KEY_PREFIX + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // ── OTP Verification ────────────────────────────────────────────────

    public void storeOtp(String email, String code, UUID userId, long ttlSeconds) {
        String key = OTP_KEY_PREFIX + email.toLowerCase();
        Map<String, String> fields =
                Map.of("code", code, "userId", userId.toString(), "attempts", "0");
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    public OtpContext getOtpContext(String email) {
        String key = OTP_KEY_PREFIX + email.toLowerCase();
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return null;
        }
        return new OtpContext(
                (String) entries.get("code"),
                UUID.fromString((String) entries.get("userId")),
                Integer.parseInt((String) entries.get("attempts")));
    }

    public int incrementAttempts(String email) {
        String key = OTP_KEY_PREFIX + email.toLowerCase();
        Long newVal = redisTemplate.opsForHash().increment(key, "attempts", 1);
        return newVal != null ? newVal.intValue() : 1;
    }

    public void deleteOtp(String email) {
        String key = OTP_KEY_PREFIX + email.toLowerCase();
        redisTemplate.delete(key);
    }

    // ── OTP Lockout ─────────────────────────────────────────────────────

    public void setLockout(String email, long ttlSeconds) {
        String key = OTP_LOCKOUT_PREFIX + email.toLowerCase();
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isLockedOut(String email) {
        String key = OTP_LOCKOUT_PREFIX + email.toLowerCase();
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // ── OTP Cooldown ────────────────────────────────────────────────────

    public void setCooldown(String email, long ttlSeconds) {
        String key = OTP_COOLDOWN_PREFIX + email.toLowerCase();
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    public boolean isOnCooldown(String email) {
        String key = OTP_COOLDOWN_PREFIX + email.toLowerCase();
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // ── OTP Context Record ──────────────────────────────────────────────

    public record OtpContext(String code, UUID userId, int attempts) {}

    // ── Password Reset Token ────────────────────────────────────────────

    public void storeResetToken(String email, String tokenHash, UUID userId, long ttlSeconds) {
        String key = RESET_TOKEN_PREFIX + email.toLowerCase();
        Map<String, String> fields = Map.of("tokenHash", tokenHash, "userId", userId.toString());
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    public ResetTokenContext getResetTokenContext(String email) {
        String key = RESET_TOKEN_PREFIX + email.toLowerCase();
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return null;
        }
        return new ResetTokenContext(
                (String) entries.get("tokenHash"), UUID.fromString((String) entries.get("userId")));
    }

    public void deleteResetToken(String email) {
        String key = RESET_TOKEN_PREFIX + email.toLowerCase();
        redisTemplate.delete(key);
    }

    public record ResetTokenContext(String tokenHash, UUID userId) {}

    // ── Password Reset Rate Limiting ────────────────────────────────────

    /**
     * Increments the password reset request count for the given email and returns true if the limit
     * has been exceeded.
     */
    public boolean isPasswordResetRateLimited(String email) {
        String key = RESET_RATE_PREFIX + email.toLowerCase();
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, RESET_RATE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        return count != null && count > RESET_RATE_MAX;
    }
}
