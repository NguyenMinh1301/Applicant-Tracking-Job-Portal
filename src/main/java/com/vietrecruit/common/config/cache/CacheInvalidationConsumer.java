package com.vietrecruit.common.config.cache;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
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
 * <p>Uses Redis SCAN (never KEYS) for pattern-based eviction. Failures are logged but never block
 * Kafka offset commits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationConsumer {

    private final CacheManager cacheManager;
    private final RedisTemplate<String, String> cacheRedisTemplate;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR)
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
        } catch (Exception e) {
            log.error(
                    "Cache eviction failed but not blocking offset commit: domain={}, entityId={}",
                    event.domain(),
                    event.entityId(),
                    e);
        }
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, CacheInvalidationEvent> record) {
        log.error(
                "Cache invalidation moved to DLT: key={}, topic={}, partition={}, offset={}",
                record.key(),
                record.topic(),
                record.partition(),
                record.offset());
    }

    private void evictJobCaches(CacheInvalidationEvent event) {
        // Evict single job detail
        if (event.entityId() != null) {
            evictCacheEntry(CacheNames.JOB_DETAIL, event.entityId().toString());
        }
        // Evict all paginated public job list caches (pattern scan)
        evictByPattern(CacheNames.JOB_PUBLIC_LIST_PREFIX + "*");
    }

    private void evictCategoryCaches(CacheInvalidationEvent event) {
        if (event.entityId() != null && event.scopeId() != null) {
            evictCacheEntry(CacheNames.CATEGORY_DETAIL, event.scopeId() + "::" + event.entityId());
        }
        // Evict company's category list
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

        RedisConnection connection = cacheRedisTemplate.getConnectionFactory().getConnection();
        try {
            Cursor<byte[]> cursor = connection.keyCommands().scan(scanOptions);
            while (cursor.hasNext()) {
                keysToDelete.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
            cursor.close();
        } finally {
            connection.close();
        }

        if (!keysToDelete.isEmpty()) {
            cacheRedisTemplate.delete(keysToDelete);
            log.debug("Evicted {} keys matching pattern: {}", keysToDelete.size(), pattern);
        }
    }
}
