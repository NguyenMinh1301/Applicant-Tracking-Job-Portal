package com.vietrecruit.feature.candidate.service;

import java.util.List;
import java.util.UUID;

import com.vietrecruit.feature.job.dto.response.JobRecommendationResponse;

public interface RecommendationService {

    List<JobRecommendationResponse> getJobRecommendations(UUID userId, int limit);
}
