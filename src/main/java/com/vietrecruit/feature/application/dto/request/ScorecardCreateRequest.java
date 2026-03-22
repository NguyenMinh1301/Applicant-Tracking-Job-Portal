package com.vietrecruit.feature.application.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.vietrecruit.feature.application.enums.ScorecardResult;

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
public class ScorecardCreateRequest {

    @NotNull(message = "Skill score is required")
    @DecimalMin(value = "0.0", message = "Skill score must be >= 0")
    private BigDecimal skillScore;

    @NotNull(message = "Attitude score is required")
    @DecimalMin(value = "0.0", message = "Attitude score must be >= 0")
    private BigDecimal attitudeScore;

    @NotNull(message = "English score is required")
    @DecimalMin(value = "0.0", message = "English score must be >= 0")
    private BigDecimal englishScore;

    @NotNull(message = "Result is required")
    private ScorecardResult result;

    @Size(max = 5000, message = "Comments must not exceed 5000 characters")
    private String comments;
}
