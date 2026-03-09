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
public class JobSummaryResponse {
    private UUID id;
    private String title;
    private JobStatus status;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private String currency;
    private Boolean isNegotiable;
    private LocalDate deadline;
    private Instant createdAt;
}
