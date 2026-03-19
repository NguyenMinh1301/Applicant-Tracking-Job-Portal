package com.vietrecruit.feature.candidate.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CandidateSearchResponse {
    private String id;
    private String headline;
    private String summary;
    private String desiredPosition;
    private String desiredPositionLevel;
    private Short yearsOfExperience;
    private String[] skills;
    private String workType;
    private Long desiredSalaryMin;
    private Long desiredSalaryMax;
    private String educationLevel;
    private String educationMajor;
    private Boolean isOpenToWork;
    private Instant updatedAt;
    private Map<String, List<String>> highlights;
    private Double score;
}
