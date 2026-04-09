package com.vietrecruit.common.config.cache;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.vietrecruit.common.config.kafka.KafkaTopicNames;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a cache invalidation event synchronously. Blocks up to {@value
     * #SEND_TIMEOUT_SECONDS}s for broker acknowledgement. On failure, throws {@link
     * RuntimeException} so the enclosing {@code @Transactional} method rolls back — preventing DB
     * commit without cache eviction.
     */
    public void publish(String domain, String action, UUID entityId, UUID scopeId) {
        var event = new CacheInvalidationEvent(domain, action, entityId, scopeId);
        String key = domain + ":" + (entityId != null ? entityId : "bulk");
        try {
            kafkaTemplate
                    .send(KafkaTopicNames.CACHE_INVALIDATION, key, event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    "Cache invalidation publish interrupted: domain="
                            + domain
                            + ", entityId="
                            + entityId,
                    e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException(
                    "Cache invalidation publish failed: domain="
                            + domain
                            + ", entityId="
                            + entityId,
                    e);
        }
    }
}
