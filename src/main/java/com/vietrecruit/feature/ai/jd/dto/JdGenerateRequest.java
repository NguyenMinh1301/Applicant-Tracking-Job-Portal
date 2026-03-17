package com.vietrecruit.feature.ai.jd.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JdGenerateRequest(
        @NotBlank @Size(max = 200) String title,
        UUID departmentId,
        @NotBlank @Size(max = 100) String employmentType,
        @NotEmpty @Size(max = 10) List<@Size(max = 500) String> keyResponsibilities,
        @NotEmpty @Size(max = 15) List<@Size(max = 100) String> requiredSkills,
        @Size(max = 10) List<@Size(max = 100) String> niceToHaveSkills,
        @Min(0) @Max(30) int yearsOfExperience,
        @NotNull JdTone tone) {}
