package com.vietrecruit.feature.ai.cv.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CvImprovementResponse(
        int overallScore,
        List<CvSuggestion> suggestions,
        List<String> strengths,
        Instant analysedAt) {}
