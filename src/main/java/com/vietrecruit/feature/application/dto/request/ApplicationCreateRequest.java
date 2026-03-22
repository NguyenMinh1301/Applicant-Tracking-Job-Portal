package com.vietrecruit.feature.application.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCreateRequest {

    @NotNull(message = "Job ID is required")
    private UUID jobId;

    @Size(max = 10000, message = "Cover letter must not exceed 10000 characters")
    private String coverLetter;
}
