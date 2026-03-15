package com.vietrecruit.feature.job.dto.response;

public record JobRecommendationResponse(
        String jobId,
        String title,
        String companyName,
        String location,
        Double matchScore,
        String matchReason) {}
