package com.vietrecruit.feature.company.dto.request;

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
public class CompanySearchRequest {
    private String q;

    @Builder.Default
    @Min(0)
    private int page = 0;

    @Builder.Default
    @Min(1)
    @Max(100)
    private int size = 20;
}
