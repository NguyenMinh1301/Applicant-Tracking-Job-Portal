package com.vietrecruit.feature.application.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.vietrecruit.common.util.LenientInstantDeserializer;

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
public class InterviewCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @NotNull(message = "Scheduled time is required")
    @Future(message = "Scheduled time must be in the future")
    @JsonDeserialize(using = LenientInstantDeserializer.class)
    private Instant scheduledAt;

    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer durationMinutes;

    @Size(max = 255, message = "Location/link must not exceed 255 characters")
    private String locationOrLink;

    @Size(max = 50, message = "Interview type must not exceed 50 characters")
    private String interviewType;

    @NotEmpty(message = "At least one interviewer is required")
    private List<UUID> interviewerIds;
}
