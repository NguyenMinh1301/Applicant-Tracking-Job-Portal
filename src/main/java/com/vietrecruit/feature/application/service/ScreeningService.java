package com.vietrecruit.feature.application.service;

import java.util.List;
import java.util.UUID;

import com.vietrecruit.feature.application.dto.response.ApplicationScreeningResponse;

public interface ScreeningService {

    List<ApplicationScreeningResponse> screenApplications(UUID jobId, UUID companyId);

    void triggerAsyncScoring(UUID jobId, UUID companyId);
}
