package com.vietrecruit.feature.application.dto.response;

import java.util.List;
import java.util.Map;
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
public class ApplicationScreeningResponse {
    private UUID applicationId;
    private UUID candidateId;
    private String candidateName;
    private String candidateEmail;
    private Double similarityScore;
    private Integer aiScore;
    private Map<String, Integer> scoreBreakdown;
    private List<String> strengths;
    private List<String> gaps;
    private String summary;
    private String applicationStatus;
}
