package com.vietrecruit.common.config.cache;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import com.vietrecruit.common.config.kafka.KafkaTopicNames;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cross-cutting Kafka consumer that handles cache invalidation for all domain mutations. Listens to
 * the centralized {@link KafkaTopicNames#CACHE_INVALIDATION} topic and evicts affected cache
 * entries.
 *
 * <p>Uses Redis SCAN (never KEYS) for pattern-based eviction. Transient Redis failures
 * (RedisConnectionFailureException, DataAccessException) are re-thrown so Kafka's default retry can
 * handle them. All other exceptions are logged with structured context and swallowed to avoid
 * blocking offset commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationConsumer {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, String> cacheRedisTemplate;

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 2000, multiplier = 2),
            dltTopicSuffix = "-dlq")
    @KafkaListener(
            topics = KafkaTopicNames.CACHE_INVALIDATION,
            groupId = "cache-invalidation-group")
    public void consume(CacheInvalidationEvent event) {
        try {
            log.debug(
                    "Cache invalidation event: domain={}, action={}, entityId={}, scopeId={}",
                    event.domain(),
                    event.action(),
                    event.entityId(),
                    event.scopeId());

            switch (event.domain()) {
                case "job" -> evictJobCaches(event);
                case "category" -> evictCategoryCaches(event);
                case "location" -> evictLocationCaches(event);
                case "company" -> evictCompanyCaches(event);
                case "plan" -> evictPlanCaches(event);
                default -> log.warn("Unknown cache invalidation domain: {}", event.domain());
            }
        } catch (DataAccessException e) {
            log.error(
                    "Transient Redis failure, re-throwing for Kafka retry: domain={}, key={}, eventType={}",
                    event.domain(),
                    event.entityId(),
                    event.action(),
                    e);
            throw e;
        } catch (Exception e) {
            log.error(
                    "Cache eviction failed (swallowed): domain={}, key={}, eventType={}",
                    event.domain(),
                    event.entityId(),
                    event.action(),
                    e);
        }
    }

    private void evictJobCaches(CacheInvalidationEvent event) {
        if (event.entityId() != null) {
            evictCacheEntry(CacheNames.JOB_DETAIL, event.entityId().toString());
        }
        evictByPattern(CacheNames.JOB_PUBLIC_LIST_PREFIX + "*");
    }

    private void evictCategoryCaches(CacheInvalidationEvent event) {
        if (event.entityId() != null && event.scopeId() != null) {
            evictCacheEntry(CacheNames.CATEGORY_DETAIL, event.scopeId() + "::" + event.entityId());
        }
        if (event.scopeId() != null) {
            evictCacheEntry(CacheNames.CATEGORY_LIST, event.scopeId().toString());
        }
    }

    private void evictLocationCaches(CacheInvalidationEvent event) {
        if (event.entityId() != null && event.scopeId() != null) {
            evictCacheEntry(CacheNames.LOCATION_DETAIL, event.scopeId() + "::" + event.entityId());
        }
        if (event.scopeId() != null) {
            evictCacheEntry(CacheNames.LOCATION_LIST, event.scopeId().toString());
        }
    }

    private void evictCompanyCaches(CacheInvalidationEvent event) {
        if (event.entityId() != null) {
            evictCacheEntry(CacheNames.COMPANY_DETAIL, event.entityId().toString());
        }
    }

    private void evictPlanCaches(CacheInvalidationEvent event) {
        evictCacheEntry(CacheNames.PLAN_LIST, "all");
        if (event.entityId() != null) {
            evictCacheEntry(CacheNames.PLAN_DETAIL, event.entityId().toString());
        }
    }

    private void evictCacheEntry(String cacheName, String key) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            log.debug("Evicted cache entry: {}::{}", cacheName, key);
        }
    }

    /**
     * Uses Redis SCAN to find and delete keys matching a pattern. Never uses KEYS command in
     * production to avoid blocking Redis.
     */
    private void evictByPattern(String pattern) {
        Set<String> keysToDelete = new HashSet<>();
        ScanOptions scanOptions = ScanOptions.scanOptions().match(pattern).count(100).build();

        cacheRedisTemplate.execute(
                (RedisCallback<Void>)
                        connection -> {
                            try (Cursor<byte[]> cursor =
                                    connection.keyCommands().scan(scanOptions)) {
                                while (cursor.hasNext()) {
                                    keysToDelete.add(
                                            new String(cursor.next(), StandardCharsets.UTF_8));
                                }
                            }
                            return null;
                        });

        if (!keysToDelete.isEmpty()) {
            cacheRedisTemplate.delete(keysToDelete);
            log.debug("Evicted {} keys matching pattern: {}", keysToDelete.size(), pattern);
        }
    }
}
