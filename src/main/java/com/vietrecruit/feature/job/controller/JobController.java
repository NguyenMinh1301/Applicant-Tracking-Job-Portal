package com.vietrecruit.feature.job.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.vietrecruit.common.response.SearchPageResponse;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.job.dto.request.JobCreateRequest;
import com.vietrecruit.feature.job.dto.request.JobSearchRequest;
import com.vietrecruit.feature.job.dto.request.JobUpdateRequest;
import com.vietrecruit.feature.job.dto.response.JobResponse;
import com.vietrecruit.feature.job.dto.response.JobSearchResponse;
import com.vietrecruit.feature.job.dto.response.JobSummaryResponse;
import com.vietrecruit.feature.job.mapper.JobMapper;
import com.vietrecruit.feature.job.service.JobSearchService;
import com.vietrecruit.feature.job.service.JobService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Job.ROOT)
@Tag(name = "Job Service", description = "Endpoints for managing job postings")
public class JobController extends BaseController {

    private final JobService jobService;
    private final JobSearchService jobSearchService;
    private final JobMapper jobMapper;

    // ── Authenticated (Employer / HR) endpoints ──────────────────────────

    @Operation(summary = "Create Job", description = "Creates a new job in DRAFT status")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PostMapping
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @Valid @RequestBody JobCreateRequest request) {
        var companyId = resolveCompanyId();
        var userId = SecurityUtils.getCurrentUserId();
        var job = jobService.createJob(companyId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                ApiSuccessCode.JOB_CREATE_SUCCESS, jobMapper.toJobResponse(job)));
    }

    @Operation(summary = "Update Job", description = "Updates a DRAFT job's fields")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PutMapping(ApiConstants.Job.UPDATE)
    public ResponseEntity<ApiResponse<JobResponse>> updateJob(
            @PathVariable UUID id, @Valid @RequestBody JobUpdateRequest request) {
        var companyId = resolveCompanyId();
        var job = jobService.updateJob(companyId, id, request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.JOB_UPDATE_SUCCESS, jobMapper.toJobResponse(job)));
    }

    @Operation(
            summary = "Publish Job",
            description = "Publishes a DRAFT job. Validates subscription and quota.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PutMapping(ApiConstants.Job.PUBLISH)
    public ResponseEntity<ApiResponse<JobResponse>> publishJob(@PathVariable UUID id) {
        var companyId = resolveCompanyId();
        var job = jobService.publishJob(companyId, id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.JOB_PUBLISH_SUCCESS, jobMapper.toJobResponse(job)));
    }

    @Operation(
            summary = "Close Job",
            description = "Closes a PUBLISHED job. Decrements active quota.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PutMapping(ApiConstants.Job.CLOSE)
    public ResponseEntity<ApiResponse<JobResponse>> closeJob(@PathVariable UUID id) {
        var companyId = resolveCompanyId();
        var job = jobService.closeJob(companyId, id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.JOB_CLOSE_SUCCESS, jobMapper.toJobResponse(job)));
    }

    @Operation(
            summary = "List Jobs",
            description = "Lists all jobs for the employer's company (paginated)")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @Parameters({
        @Parameter(name = "page", description = "Page number (0-based)", example = "0"),
        @Parameter(name = "size", description = "Page size", example = "20"),
        @Parameter(
                name = "sort",
                description = "Sort field and direction",
                example = "createdAt,desc")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<JobSummaryResponse>>> listJobs(
            @ParameterObject
                    @PageableDefault(
                            page = 0,
                            size = 20,
                            sort = "createdAt",
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        var companyId = resolveCompanyId();
        var page = jobService.listJobs(companyId, pageable).map(jobMapper::toJobSummaryResponse);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.JOB_LIST_SUCCESS, PageResponse.from(page)));
    }

    @Operation(
            summary = "Get Job",
            description = "Gets a single job detail owned by the employer's company")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.Job.GET)
    public ResponseEntity<ApiResponse<JobResponse>> getJob(@PathVariable UUID id) {
        var companyId = resolveCompanyId();
        var job = jobService.getJob(companyId, id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.JOB_FETCH_SUCCESS, jobMapper.toJobResponse(job)));
    }

    // ── Search (unauthenticated) endpoints ──────────────────────────────

    @Operation(
            summary = "Search Jobs",
            description =
                    "Full-text search across published jobs with Vietnamese language support, fuzzy matching, and intelligent ranking")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.Job.SEARCH)
    public ResponseEntity<ApiResponse<SearchPageResponse<JobSearchResponse>>> searchJobs(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Double salaryMin,
            @RequestParam(required = false) Double salaryMax,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "relevance") String sort) {
        var request =
                JobSearchRequest.builder()
                        .q(q)
                        .locationId(locationId)
                        .categoryId(categoryId)
                        .salaryMin(salaryMin)
                        .salaryMax(salaryMax)
                        .currency(currency)
                        .page(page)
                        .size(size)
                        .sort(sort)
                        .build();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.SEARCH_SUCCESS, jobSearchService.search(request)));
    }

    @Operation(
            summary = "Autocomplete Jobs",
            description = "Returns title suggestions for type-ahead search")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.Job.AUTOCOMPLETE)
    public ResponseEntity<ApiResponse<List<String>>> autocompleteJobs(
            @RequestParam String q, @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.AUTOCOMPLETE_SUCCESS,
                        jobSearchService.autocomplete(q, limit)));
    }

    // ── Public (unauthenticated) endpoints ───────────────────────────────

    @Operation(
            summary = "List Public Jobs",
            description = "Lists published jobs with optional filters (public)")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @Parameters({
        @Parameter(name = "page", description = "Page number (0-based)", example = "0"),
        @Parameter(name = "size", description = "Page size", example = "20"),
        @Parameter(
                name = "sort",
                description = "Sort field and direction",
                example = "createdAt,desc")
    })
    @GetMapping(ApiConstants.Job.PUBLIC_ROOT)
    public ResponseEntity<ApiResponse<PageResponse<JobSummaryResponse>>> listPublicJobs(
            @ParameterObject
                    @PageableDefault(
                            page = 0,
                            size = 20,
                            sort = "createdAt",
                            direction = Sort.Direction.DESC)
                    Pageable pageable,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String keyword) {
        var page =
                jobService
                        .listPublicJobs(pageable, categoryId, locationId, keyword)
                        .map(jobMapper::toJobSummaryResponse);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.JOB_LIST_SUCCESS, PageResponse.from(page)));
    }

    @Operation(
            summary = "Get Public Job",
            description = "Gets a single published job detail (public)")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.Job.PUBLIC_GET)
    public ResponseEntity<ApiResponse<JobResponse>> getPublicJob(@PathVariable UUID id) {
        var job = jobService.getPublicJob(id);
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.JOB_FETCH_SUCCESS, jobMapper.toJobResponse(job)));
    }
}
