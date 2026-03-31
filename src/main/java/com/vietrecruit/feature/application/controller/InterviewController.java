package com.vietrecruit.feature.application.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.application.dto.request.InterviewCreateRequest;
import com.vietrecruit.feature.application.dto.request.InterviewStatusUpdateRequest;
import com.vietrecruit.feature.application.dto.response.InterviewResponse;
import com.vietrecruit.feature.application.service.InterviewService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "Interview Service", description = "Endpoints for managing interviews")
public class InterviewController extends BaseController {

    private final InterviewService interviewService;

    @Operation(
            summary = "Schedule Interview",
            description = "HR schedules an interview for an application in INTERVIEW status")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @PostMapping(ApiConstants.Application.ROOT + ApiConstants.Application.INTERVIEWS)
    public ResponseEntity<ApiResponse<InterviewResponse>> scheduleInterview(
            @PathVariable UUID id, @Valid @RequestBody InterviewCreateRequest request) {
        var companyId = resolveCompanyId();
        var userId = SecurityUtils.getCurrentUserId();
        var response = interviewService.scheduleInterview(id, companyId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.INTERVIEW_CREATE_SUCCESS, response));
    }

    @Operation(
            summary = "List Interviews",
            description =
                    "HR lists all interviews for an application; CANDIDATE can view own application's interviews")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN', 'ROLE_CANDIDATE')")
    @GetMapping(ApiConstants.Application.ROOT + ApiConstants.Application.INTERVIEWS)
    public ResponseEntity<ApiResponse<List<InterviewResponse>>> listInterviews(
            @PathVariable UUID id) {
        UUID companyId =
                SecurityUtils.getCurrentRoles().contains("CANDIDATE") ? null : resolveCompanyId();
        var response = interviewService.listInterviews(id, companyId);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.INTERVIEW_LIST_SUCCESS, response));
    }

    @Operation(
            summary = "My Interviews",
            description = "Interviewer lists their own assigned interviews")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasRole('INTERVIEWER')")
    @GetMapping(ApiConstants.Interview.ROOT + "/mine")
    public ResponseEntity<ApiResponse<List<InterviewResponse>>> getMyInterviews() {
        var userId = SecurityUtils.getCurrentUserId();
        var response = interviewService.getInterviewsByInterviewer(userId);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.INTERVIEW_LIST_SUCCESS, response));
    }

    @Operation(
            summary = "Get Interview",
            description = "HR, assigned interviewer, or candidate views interview detail")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.Interview.ROOT + ApiConstants.Interview.GET)
    public ResponseEntity<ApiResponse<InterviewResponse>> getInterview(@PathVariable UUID id) {
        var userId = SecurityUtils.getCurrentUserId();
        var response = interviewService.getInterview(id, userId);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.INTERVIEW_FETCH_SUCCESS, response));
    }

    @Operation(
            summary = "Update Interview Status",
            description = "HR completes or cancels an interview")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @PutMapping(ApiConstants.Interview.ROOT + ApiConstants.Interview.STATUS)
    public ResponseEntity<ApiResponse<InterviewResponse>> updateInterviewStatus(
            @PathVariable UUID id, @Valid @RequestBody InterviewStatusUpdateRequest request) {
        var companyId = resolveCompanyId();
        var userId = SecurityUtils.getCurrentUserId();
        var response = interviewService.updateInterviewStatus(id, companyId, userId, request);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.INTERVIEW_STATUS_UPDATE_SUCCESS, response));
    }
}
