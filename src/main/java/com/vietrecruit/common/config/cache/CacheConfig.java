package com.vietrecruit.common.config.cache;

import java.time.Duration;
import java.util.Map;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Configures Redis-backed Spring Cache with per-cache TTLs and a dedicated string {@link
 * org.springframework.data.redis.core.RedisTemplate} for low-level key operations.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // NON_FINAL adds type info for all non-final types (ArrayList, PageImpl, DTOs, etc.).
        // OBJECT_AND_NON_CONCRETE is unsuitable: it skips concrete types like ArrayList during
        // serialization (no type wrapper written) but expects type info during deserialization
        // (target type is Object.class), causing MismatchedInputException.
        // Caveat: List.of() / Map.of() return package-private final JDK types that cannot be
        // deserialized — always use Collectors.toList() or new ArrayList<>() in cached methods.
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.vietrecruit.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.math.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType("org.springframework.data.")
                        .allowIfSubTypeIsArray()
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(cacheObjectMapper());

        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        new StringRedisSerializer()))
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        jsonSerializer))
                        .disableCachingNullValues()
                        .entryTtl(Duration.ofMinutes(10));

        Map<String, RedisCacheConfiguration> perCacheConfig =
                Map.of(
                        CacheNames.JOB_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(10)),
                        CacheNames.CATEGORY_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(30)),
                        CacheNames.CATEGORY_LIST, defaultConfig.entryTtl(Duration.ofMinutes(30)),
                        CacheNames.LOCATION_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(30)),
                        CacheNames.LOCATION_LIST, defaultConfig.entryTtl(Duration.ofMinutes(30)),
                        CacheNames.COMPANY_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(15)),
                        CacheNames.PLAN_LIST, defaultConfig.entryTtl(Duration.ofHours(1)),
                        CacheNames.PLAN_DETAIL, defaultConfig.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfig)
                .transactionAware()
                .build();
    }

    @Bean
    public RedisTemplate<String, String> cacheRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
