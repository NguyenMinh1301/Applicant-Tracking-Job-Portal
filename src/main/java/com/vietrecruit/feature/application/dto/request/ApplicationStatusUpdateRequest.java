package com.vietrecruit.feature.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.vietrecruit.feature.application.enums.ApplicationStatus;

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
public class ApplicationStatusUpdateRequest {

    @NotNull(message = "Target status is required")
    private ApplicationStatus status;

    @Size(max = 5000, message = "Notes must not exceed 5000 characters")
    private String notes;
}
