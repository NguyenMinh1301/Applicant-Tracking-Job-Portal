package com.vietrecruit.feature.job.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
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
public class JobCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    private String requirements;

    private UUID departmentId;

    private UUID locationId;

    private UUID categoryId;

    private BigDecimal minSalary;

    private BigDecimal maxSalary;

    @Size(max = 10, message = "Currency code must not exceed 10 characters")
    private String currency;

    private Boolean isNegotiable;

    private LocalDate deadline;
}
