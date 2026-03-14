package com.vietrecruit.feature.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.vietrecruit.common.response.PageResponse;
import com.vietrecruit.feature.application.dto.request.ApplicationCreateRequest;
import com.vietrecruit.feature.application.dto.request.ApplicationStatusUpdateRequest;
import com.vietrecruit.feature.application.dto.response.ApplicationResponse;
import com.vietrecruit.feature.application.dto.response.ApplicationStatusHistoryResponse;
import com.vietrecruit.feature.application.dto.response.ApplicationSummaryResponse;
import com.vietrecruit.feature.application.enums.ApplicationStatus;

public interface ApplicationService {

    ApplicationResponse apply(UUID userId, ApplicationCreateRequest request);

    ApplicationResponse getApplication(UUID applicationId, UUID userId);

    PageResponse<ApplicationSummaryResponse> listApplications(
            UUID companyId, UUID jobId, ApplicationStatus status, Pageable pageable);

    PageResponse<ApplicationSummaryResponse> listMyApplications(UUID userId, Pageable pageable);

    ApplicationResponse updateStatus(
            UUID applicationId,
            UUID companyId,
            UUID userId,
            ApplicationStatusUpdateRequest request);

    List<ApplicationStatusHistoryResponse> getStatusHistory(UUID applicationId, UUID companyId);

    void insertHistory(
            UUID applicationId,
            ApplicationStatus oldStatus,
            ApplicationStatus newStatus,
            UUID changedBy,
            String notes);
}
