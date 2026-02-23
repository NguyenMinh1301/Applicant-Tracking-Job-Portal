package com.vietrecruit.common.security;

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

    private final StringRedisTemplate redisTemplate;

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
}
