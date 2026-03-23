package com.vietrecruit.common.config.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

@ExtendWith(MockitoExtension.class)
class CacheInvalidationConsumerTest {

    @Mock private CacheManager cacheManager;

    @SuppressWarnings("unchecked")
    @Mock
    private RedisTemplate<String, String> cacheRedisTemplate;

    @Mock private RedisConnectionFactory connectionFactory;
    @Mock private RedisConnection connection;
    @Mock private RedisKeyCommands keyCommands;

    @SuppressWarnings("unchecked")
    @Mock
    private Cursor<byte[]> cursor;

    @Mock private Cache jobDetailCache;
    @Mock private Cache companyDetailCache;

    @InjectMocks private CacheInvalidationConsumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUpRedisChain() {
        lenient().when(connection.keyCommands()).thenReturn(keyCommands);
        // Mock execute(RedisCallback) to invoke the callback with the mocked connection,
        // mirroring RedisTemplate's real behaviour of obtaining + releasing a connection.
        lenient()
                .doAnswer(
                        invocation -> {
                            RedisCallback<?> callback = invocation.getArgument(0);
                            try {
                                callback.doInRedis(connection);
                            } finally {
                                connection.close();
                            }
                            return null;
                        })
                .when(cacheRedisTemplate)
                .execute(any(RedisCallback.class));
    }

    // ── Scenario 1 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Job domain event → cacheManager.evict for detail, SCAN+DEL for job list pattern")
    @SuppressWarnings("unchecked")
    void consume_jobDomainEvent_scanAndDeleteCalled() {
        UUID entityId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        CacheInvalidationEvent event =
                new CacheInvalidationEvent("job", "updated", entityId, companyId);

        byte[] matchingKey = (CacheNames.JOB_PUBLIC_LIST_PREFIX + "page1").getBytes();

        when(cacheManager.getCache(CacheNames.JOB_DETAIL)).thenReturn(jobDetailCache);
        when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(matchingKey);

        consumer.consume(event);

        // detail eviction via CacheManager
        verify(jobDetailCache).evict(entityId.toString());

        // pattern eviction: SCAN was used
        verify(keyCommands).scan(any(ScanOptions.class));

        // DEL was called with the collected keys
        verify(cacheRedisTemplate).delete(argThat((Set<String> keys) -> keys.size() == 1));

        // connection always closed
        verify(connection).close();
    }

    // ── Scenario 2 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("SCAN returns multiple pages (multiple keys) → all keys collected and deleted")
    @SuppressWarnings("unchecked")
    void consume_scanReturnsMultipleKeys_allCollectedAndDeleted() {
        UUID entityId = UUID.randomUUID();
        CacheInvalidationEvent event = new CacheInvalidationEvent("job", "closed", entityId, null);

        when(cacheManager.getCache(CacheNames.JOB_DETAIL)).thenReturn(jobDetailCache);
        when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
        // Simulate 3 keys then stop
        when(cursor.hasNext()).thenReturn(true, true, true, false);
        when(cursor.next())
                .thenReturn("jobs::list::1".getBytes())
                .thenReturn("jobs::list::2".getBytes())
                .thenReturn("jobs::list::3".getBytes());

        consumer.consume(event);

        verify(cacheRedisTemplate).delete(argThat((Set<String> keys) -> keys.size() == 3));
        verify(connection).close();
    }

    // ── Scenario 3 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName(
            "RedisConnectionException mid-SCAN → re-thrown as DataAccessException, connection closed")
    @SuppressWarnings("unchecked")
    void consume_redisConnectionExceptionDuringScan_rethrown_connectionClosed() {
        UUID entityId = UUID.randomUUID();
        CacheInvalidationEvent event = new CacheInvalidationEvent("job", "updated", entityId, null);

        when(cacheManager.getCache(CacheNames.JOB_DETAIL)).thenReturn(jobDetailCache);
        when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenThrow(new RedisConnectionFailureException("Connection refused"));

        // DataAccessException re-thrown by consumer
        assertThrows(DataAccessException.class, () -> consumer.consume(event));

        // connection.close() called in finally — no leak
        verify(connection).close();

        // cacheRedisTemplate.delete() never reached
        verify(cacheRedisTemplate, never()).delete(anyCollection());
    }

    // ── Scenario 4 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("SCAN returns no keys → cacheRedisTemplate.delete() never called")
    @SuppressWarnings("unchecked")
    void consume_scanReturnsNoKeys_deleteNotCalled() {
        UUID entityId = UUID.randomUUID();
        CacheInvalidationEvent event = new CacheInvalidationEvent("job", "created", entityId, null);

        when(cacheManager.getCache(CacheNames.JOB_DETAIL)).thenReturn(jobDetailCache);
        when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false); // empty result immediately

        assertDoesNotThrow(() -> consumer.consume(event));

        verify(cacheRedisTemplate, never()).delete(anyCollection());
        verify(connection).close();
    }

    // ── Other domain events (no SCAN needed) ──────────────────────────────

    @Test
    @DisplayName("Company domain event → evictCacheEntry via CacheManager, no SCAN")
    void consume_companyDomainEvent_evictCalled_noScan() {
        UUID entityId = UUID.randomUUID();
        CacheInvalidationEvent event =
                new CacheInvalidationEvent("company", "updated", entityId, null);

        when(cacheManager.getCache(CacheNames.COMPANY_DETAIL)).thenReturn(companyDetailCache);

        assertDoesNotThrow(() -> consumer.consume(event));

        verify(companyDetailCache).evict(entityId.toString());
        verifyNoInteractions(connectionFactory);
    }

    @Test
    @DisplayName("Unknown domain → logged as warn, no exception, no cache operation")
    void consume_unknownDomain_logsWarnNoException() {
        CacheInvalidationEvent event =
                new CacheInvalidationEvent("unknown_domain", "updated", UUID.randomUUID(), null);

        assertDoesNotThrow(() -> consumer.consume(event));

        verifyNoInteractions(jobDetailCache, companyDetailCache, connectionFactory);
    }

    @Test
    @DisplayName("Plan domain event → plan list and detail evicted")
    void consume_planDomainEvent_planCachesEvicted() {
        UUID planId = UUID.randomUUID();
        CacheInvalidationEvent event = new CacheInvalidationEvent("plan", "updated", planId, null);
        Cache planListCache = mock(Cache.class);
        Cache planDetailCache = mock(Cache.class);

        when(cacheManager.getCache(CacheNames.PLAN_LIST)).thenReturn(planListCache);
        when(cacheManager.getCache(CacheNames.PLAN_DETAIL)).thenReturn(planDetailCache);

        assertDoesNotThrow(() -> consumer.consume(event));

        verify(planListCache).evict("all");
        verify(planDetailCache).evict(planId.toString());
    }
}
