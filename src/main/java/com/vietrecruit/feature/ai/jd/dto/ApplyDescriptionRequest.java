package com.vietrecruit.feature.ai.jd.dto;

import jakarta.validation.constraints.NotNull;

public record ApplyDescriptionRequest(@NotNull GeneratedJobDescription generatedDescription) {}
