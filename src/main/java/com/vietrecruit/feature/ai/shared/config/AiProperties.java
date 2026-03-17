package com.vietrecruit.feature.ai.shared.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "spring.ai.openai")
@Validated
public record AiProperties(@NotBlank String apiKey) {}
