package com.vietrecruit.common.config.kafka;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopicNames {

    // ========== Debezium CDC Topics ==========

    public static final String CDC_USER = "vietrecruit.public.users";
    public static final String CDC_JOB = "vietrecruit.public.jobs";
    public static final String CDC_CANDIDATE = "vietrecruit.public.candidates";
    public static final String CDC_COMPANY = "vietrecruit.public.companies";

    // ========== Application Topics ==========

    public static final String NOTIFICATION_EMAIL = "notification.email";

    // ========== Cache Invalidation Topics ==========

    public static final String CACHE_INVALIDATION = "cache.invalidation";
}
