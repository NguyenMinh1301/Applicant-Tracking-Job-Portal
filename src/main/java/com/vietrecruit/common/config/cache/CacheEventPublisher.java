package com.vietrecruit.common.config.cache;

import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.vietrecruit.common.config.kafka.KafkaTopicNames;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String domain, String action, UUID entityId, UUID scopeId) {
        var event = new CacheInvalidationEvent(domain, action, entityId, scopeId);
        String key = domain + ":" + (entityId != null ? entityId : "bulk");
        kafkaTemplate
                .send(KafkaTopicNames.CACHE_INVALIDATION, key, event)
                .whenComplete(
                        (result, ex) -> {
                            if (ex != null) {
                                log.warn(
                                        "Failed to publish cache invalidation event: domain={}, action={}, entityId={}",
                                        domain,
                                        action,
                                        entityId,
                                        ex);
                            }
                        });
    }
}
