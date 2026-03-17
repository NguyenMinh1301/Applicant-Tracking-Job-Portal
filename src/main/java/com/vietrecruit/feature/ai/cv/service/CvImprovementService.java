package com.vietrecruit.feature.ai.cv.service;

import java.util.UUID;

import com.vietrecruit.feature.ai.cv.dto.CvImprovementResponse;

public interface CvImprovementService {

    CvImprovementResponse analyze(UUID userId);
}
