package com.vietrecruit.feature.ai.jd.dto;

import java.time.Instant;
import java.util.List;

public record JdGenerateResponse(
        String title,
        GeneratedJobDescription generatedDescription,
        List<String> biasFlags,
        Instant generatedAt) {}
