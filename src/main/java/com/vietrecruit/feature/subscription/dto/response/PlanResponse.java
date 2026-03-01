package com.vietrecruit.feature.subscription.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

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
public class PlanResponse {
    private UUID id;
    private String code;
    private String name;
    private String description;
    private Integer maxActiveJobs;
    private Integer jobDurationDays;
    private Boolean resumeAccess;
    private Boolean aiMatching;
    private Boolean priorityListing;
    private BigDecimal priceMonthly;
    private BigDecimal priceYearly;
    private String currency;
}
