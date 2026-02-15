package com.vietrecruit.common.config.kafka;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopicNames {

    // ========== Debezium CDC Topics ==========

    public static final String CDC_USER = "vietrecruit.public.users";

    // ========== Application Topics ==========
    // Add application topics here as needed
}
