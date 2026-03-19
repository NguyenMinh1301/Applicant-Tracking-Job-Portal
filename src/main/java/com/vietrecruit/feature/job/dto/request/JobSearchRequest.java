package com.vietrecruit.feature.job.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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
public class JobSearchRequest {
    private String q;
    private UUID locationId;
    private UUID categoryId;
    private Double salaryMin;
    private Double salaryMax;
    private String currency;

    @Builder.Default
    @Min(0)
    private int page = 0;

    @Builder.Default
    @Min(1)
    @Max(100)
    private int size = 20;

    @Builder.Default private String sort = "relevance";
}
