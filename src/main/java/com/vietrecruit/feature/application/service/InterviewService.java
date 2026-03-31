package com.vietrecruit.feature.application.service;

import java.util.List;
import java.util.UUID;

import com.vietrecruit.feature.application.dto.request.InterviewCreateRequest;
import com.vietrecruit.feature.application.dto.request.InterviewStatusUpdateRequest;
import com.vietrecruit.feature.application.dto.response.InterviewResponse;

public interface InterviewService {

    InterviewResponse scheduleInterview(
            UUID applicationId, UUID companyId, UUID createdBy, InterviewCreateRequest request);

    List<InterviewResponse> listInterviews(UUID applicationId, UUID companyId);

    InterviewResponse getInterview(UUID interviewId, UUID userId);

    InterviewResponse updateInterviewStatus(
            UUID interviewId, UUID companyId, UUID userId, InterviewStatusUpdateRequest request);

    List<InterviewResponse> getInterviewsByInterviewer(UUID userId);
}
