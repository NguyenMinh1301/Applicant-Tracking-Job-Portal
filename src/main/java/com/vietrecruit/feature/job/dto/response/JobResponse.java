package com.vietrecruit.feature.job.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.vietrecruit.feature.job.enums.JobStatus;

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
public class JobResponse {
    private UUID id;
    private UUID departmentId;
    private UUID locationId;
    private UUID categoryId;
    private String title;
    private String description;
    private String requirements;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private String currency;
    private Boolean isNegotiable;
    private JobStatus status;
    private LocalDate deadline;
    private String publicLink;
    private UUID createdBy;
    private UUID updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
