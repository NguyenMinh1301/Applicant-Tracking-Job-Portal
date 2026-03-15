package com.vietrecruit.feature.application.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.response.PageResponse;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.application.dto.request.ApplicationCreateRequest;
import com.vietrecruit.feature.application.dto.request.ApplicationStatusUpdateRequest;
import com.vietrecruit.feature.application.dto.response.ApplicationResponse;
import com.vietrecruit.feature.application.dto.response.ApplicationScreeningResponse;
import com.vietrecruit.feature.application.dto.response.ApplicationStatusHistoryResponse;
import com.vietrecruit.feature.application.dto.response.ApplicationSummaryResponse;
import com.vietrecruit.feature.application.enums.ApplicationStatus;
import com.vietrecruit.feature.application.service.ApplicationService;
import com.vietrecruit.feature.application.service.ScreeningService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Application.ROOT)
@Tag(name = "Application Service", description = "Endpoints for managing job applications")
public class ApplicationController extends BaseController {

    private final ApplicationService applicationService;
    private final ScreeningService screeningService;

    @Operation(
            summary = "Apply to Job",
            description = "Candidate submits an application to a published job")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAuthority('ROLE_CANDIDATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(
            @Valid @RequestBody ApplicationCreateRequest request) {
        var userId = SecurityUtils.getCurrentUserId();
        var response = applicationService.apply(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.APPLICATION_CREATE_SUCCESS, response));
    }

    @Operation(
            summary = "List Applications",
            description = "HR lists applications for their company (paginated, filtered)")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @Parameters({
        @Parameter(name = "page", description = "Page number (0-based)", example = "0"),
        @Parameter(name = "size", description = "Page size", example = "20"),
        @Parameter(
                name = "sort",
                description = "Sort field and direction",
                example = "createdAt,desc")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ApplicationSummaryResponse>>> listApplications(
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) ApplicationStatus status,
            @ParameterObject
                    @PageableDefault(
                            page = 0,
                            size = 20,
                            sort = "createdAt",
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        var companyId = resolveCompanyId();
        var response = applicationService.listApplications(companyId, jobId, status, pageable);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.APPLICATION_LIST_SUCCESS, response));
    }

    @Operation(summary = "My Applications", description = "Candidate views their own applications")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAuthority('ROLE_CANDIDATE')")
    @Parameters({
        @Parameter(name = "page", description = "Page number (0-based)", example = "0"),
        @Parameter(name = "size", description = "Page size", example = "20"),
        @Parameter(
                name = "sort",
                description = "Sort field and direction",
                example = "createdAt,desc")
    })
    @GetMapping(ApiConstants.Application.MINE)
    public ResponseEntity<ApiResponse<PageResponse<ApplicationSummaryResponse>>> listMyApplications(
            @ParameterObject
                    @PageableDefault(
                            page = 0,
                            size = 20,
                            sort = "createdAt",
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        var userId = SecurityUtils.getCurrentUserId();
        var response = applicationService.listMyApplications(userId, pageable);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.APPLICATION_LIST_SUCCESS, response));
    }

    @Operation(
            summary = "Get Application",
            description = "HR or candidate (own) views application detail")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.Application.GET)
    public ResponseEntity<ApiResponse<ApplicationResponse>> getApplication(@PathVariable UUID id) {
        var userId = SecurityUtils.getCurrentUserId();
        var response = applicationService.getApplication(id, userId);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.APPLICATION_FETCH_SUCCESS, response));
    }

    @Operation(
            summary = "Update Application Status",
            description = "HR updates application pipeline status")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @PutMapping(ApiConstants.Application.STATUS)
    public ResponseEntity<ApiResponse<ApplicationResponse>> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody ApplicationStatusUpdateRequest request) {
        var companyId = resolveCompanyId();
        var userId = SecurityUtils.getCurrentUserId();
        var response = applicationService.updateStatus(id, companyId, userId, request);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.APPLICATION_STATUS_UPDATE_SUCCESS, response));
    }

    @Operation(
            summary = "Get Screening Results",
            description = "HR retrieves cached AI screening scores for all applications of a job")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @GetMapping(ApiConstants.Application.SCREENING)
    public ResponseEntity<ApiResponse<List<ApplicationScreeningResponse>>> getScreeningResults(
            @PathVariable UUID jobId) {
        var companyId = resolveCompanyId();
        var response = screeningService.screenApplications(jobId, companyId);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.APPLICATION_SCREENING_SUCCESS, response));
    }

    @Operation(
            summary = "Trigger AI Screening",
            description = "HR triggers async AI scoring for unscored applications of a job")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @PostMapping(ApiConstants.Application.SCREENING_TRIGGER)
    public ResponseEntity<ApiResponse<String>> triggerScreening(@PathVariable UUID jobId) {
        var companyId = resolveCompanyId();
        screeningService.triggerAsyncScoring(jobId, companyId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(
                        ApiResponse.success(
                                ApiSuccessCode.APPLICATION_SCREENING_TRIGGER_SUCCESS,
                                "Scoring in progress"));
    }

    @Operation(
            summary = "Get Status History",
            description = "HR views the status transition history of an application")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @GetMapping(ApiConstants.Application.STATUS_HISTORY)
    public ResponseEntity<ApiResponse<List<ApplicationStatusHistoryResponse>>> getStatusHistory(
            @PathVariable UUID id) {
        var companyId = resolveCompanyId();
        var response = applicationService.getStatusHistory(id, companyId);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.APPLICATION_STATUS_HISTORY_SUCCESS, response));
    }
}
