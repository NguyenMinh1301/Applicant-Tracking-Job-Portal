package com.vietrecruit.common.config.cache;

import java.util.UUID;

/**
 * Lightweight Kafka event for cache invalidation. Published by domain services after mutations,
 * consumed by {@link CacheInvalidationConsumer} to evict affected cache entries.
 *
 * @param domain the domain that changed (e.g., "job", "category", "company")
 * @param action the mutation action (e.g., "created", "updated", "closed", "deleted")
 * @param entityId the primary entity ID (nullable for bulk operations)
 * @param scopeId the scoping identifier — typically companyId (nullable for global entities)
 */
public record CacheInvalidationEvent(String domain, String action, UUID entityId, UUID scopeId) {}
