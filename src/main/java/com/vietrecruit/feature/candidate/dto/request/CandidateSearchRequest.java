package com.vietrecruit.feature.candidate.dto.request;

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
public class CandidateSearchRequest {
    private String q;
    private String[] skills;
    private Short experienceMin;
    private Boolean isOpenToWork;
    private String educationLevel;
    private String workType;

    @Builder.Default
    @Min(0)
    private int page = 0;

    @Builder.Default
    @Min(1)
    @Max(100)
    private int size = 20;
}
