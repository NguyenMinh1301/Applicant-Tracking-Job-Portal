package com.vietrecruit.feature.ai.salary.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalaryBenchmarkResponse(
        String jobTitle,
        String location,
        String experienceLevel,
        String currency,
        SalaryRange range,
        String marketPosition,
        int dataPoints,
        List<String> insights,
        String disclaimer,
        Instant generatedAt) {}
