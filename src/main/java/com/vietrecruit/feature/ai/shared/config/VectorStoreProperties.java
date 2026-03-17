package com.vietrecruit.feature.ai.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "spring.ai.vectorstore.pgvector")
@Validated
public record VectorStoreProperties(
        @NotNull @Positive Integer dimensions,
        @NotBlank String distanceType,
        @NotBlank String indexType) {}
