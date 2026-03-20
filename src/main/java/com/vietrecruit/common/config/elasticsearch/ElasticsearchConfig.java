package com.vietrecruit.common.config.elasticsearch;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;

/**
 * Provides a custom {@link JsonpMapper} bean so the Elasticsearch Java API client serializes Java 8
 * date/time types (e.g. {@link java.time.Instant}) correctly.
 *
 * <p>Spring Boot's {@code ElasticsearchClientAutoConfiguration} is annotated with
 * {@code @ConditionalOnMissingBean(JsonpMapper.class)}, so this bean takes precedence over the
 * default {@link JacksonJsonpMapper} that omits {@link JavaTimeModule}.
 */
@Configuration
public class ElasticsearchConfig {

    @Bean
    public JsonpMapper elasticsearchJsonpMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new JacksonJsonpMapper(mapper);
    }
}
