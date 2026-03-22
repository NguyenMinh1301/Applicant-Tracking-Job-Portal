package com.vietrecruit.feature.application.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
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
public class OfferCreateRequest {

    @NotNull(message = "Base salary is required")
    @DecimalMin(value = "0.01", message = "Base salary must be positive")
    private BigDecimal baseSalary;

    @Size(max = 10, message = "Currency code must not exceed 10 characters")
    private String currency;

    private LocalDate startDate;

    @Size(max = 5000, message = "Note must not exceed 5000 characters")
    private String note;

    @Size(max = 2048, message = "Offer letter URL must not exceed 2048 characters")
    @jakarta.validation.constraints.Pattern(
            regexp = "^https://.*",
            message = "Offer letter URL must start with https://")
    private String offerLetterUrl;
}
