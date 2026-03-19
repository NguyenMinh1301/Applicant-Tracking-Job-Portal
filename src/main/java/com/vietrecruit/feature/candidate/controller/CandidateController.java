package com.vietrecruit.feature.candidate.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.response.SearchPageResponse;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.candidate.dto.request.CandidateSearchRequest;
import com.vietrecruit.feature.candidate.dto.request.CandidateUpdateRequest;
import com.vietrecruit.feature.candidate.dto.response.CandidateProfileResponse;
import com.vietrecruit.feature.candidate.dto.response.CandidateSearchResponse;
import com.vietrecruit.feature.candidate.dto.response.CvUploadResponse;
import com.vietrecruit.feature.candidate.service.CandidateSearchService;
import com.vietrecruit.feature.candidate.service.CandidateService;
import com.vietrecruit.feature.candidate.service.RecommendationService;
import com.vietrecruit.feature.job.dto.response.JobRecommendationResponse;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Candidate.ROOT)
@Tag(name = "Candidate", description = "Endpoints for managing candidate profiles and CV uploads")
public class CandidateController extends BaseController {

    private final CandidateService candidateService;
    private final CandidateSearchService candidateSearchService;
    private final RecommendationService recommendationService;

    @Operation(
            summary = "Get My Profile",
            description = "Retrieves the authenticated candidate's profile")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasRole('CANDIDATE')")
    @GetMapping(ApiConstants.Candidate.ME)
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> getProfile() {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.CANDIDATE_FETCH_SUCCESS,
                        candidateService.getProfile(userId)));
    }

    @Operation(
            summary = "Update My Profile",
            description = "Updates the authenticated candidate's profile fields")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasRole('CANDIDATE')")
    @PutMapping(ApiConstants.Candidate.ME)
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> updateProfile(
            @Valid @RequestBody CandidateUpdateRequest request) {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.CANDIDATE_UPDATE_SUCCESS,
                        candidateService.updateProfile(userId, request)));
    }

    @Operation(
            summary = "Upload CV",
            description =
                    "Uploads or replaces the candidate's CV. Accepted: PDF, DOCX, JPEG, PNG. Max 5MB.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasRole('CANDIDATE')")
    @PostMapping(ApiConstants.Candidate.ME_CV)
    public ResponseEntity<ApiResponse<CvUploadResponse>> uploadCv(
            @RequestParam("file") MultipartFile file) {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.CANDIDATE_CV_UPLOAD_SUCCESS,
                        candidateService.uploadCv(userId, file)));
    }

    @Operation(summary = "Delete CV", description = "Removes the candidate's CV from storage")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasRole('CANDIDATE')")
    @DeleteMapping(ApiConstants.Candidate.ME_CV)
    public ResponseEntity<ApiResponse<Void>> deleteCv() {
        var userId = SecurityUtils.getCurrentUserId();
        candidateService.deleteCv(userId);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.CANDIDATE_CV_DELETE_SUCCESS));
    }

    @Operation(
            summary = "Get Job Recommendations",
            description =
                    "Returns semantically matched job recommendations based on the candidate's CV")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasRole('CANDIDATE')")
    @GetMapping(ApiConstants.Candidate.ME_JOB_RECOMMENDATIONS)
    public ResponseEntity<ApiResponse<List<JobRecommendationResponse>>> getJobRecommendations(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.CANDIDATE_JOB_RECOMMENDATIONS_SUCCESS,
                        recommendationService.getJobRecommendations(userId, limit)));
    }

    @Operation(
            summary = "Search Candidates",
            description = "Full-text search across candidate profiles (employer-only)")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyRole('HR', 'COMPANY_ADMIN', 'SYSTEM_ADMIN')")
    @GetMapping(ApiConstants.Candidate.SEARCH)
    public ResponseEntity<ApiResponse<SearchPageResponse<CandidateSearchResponse>>>
            searchCandidates(
                    @RequestParam(required = false) String q,
                    @RequestParam(required = false) String[] skills,
                    @RequestParam(required = false) Short experienceMin,
                    @RequestParam(required = false) Boolean isOpenToWork,
                    @RequestParam(required = false) String educationLevel,
                    @RequestParam(required = false) String workType,
                    @RequestParam(defaultValue = "0") int page,
                    @RequestParam(defaultValue = "20") int size) {
        var request =
                CandidateSearchRequest.builder()
                        .q(q)
                        .skills(skills)
                        .experienceMin(experienceMin)
                        .isOpenToWork(isOpenToWork)
                        .educationLevel(educationLevel)
                        .workType(workType)
                        .page(page)
                        .size(size)
                        .build();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.SEARCH_SUCCESS, candidateSearchService.search(request)));
    }

    @Operation(
            summary = "Get Candidate by ID",
            description = "Retrieves a candidate's profile by ID (HR/Admin access)")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyRole('HR', 'COMPANY_ADMIN', 'SYSTEM_ADMIN')")
    @GetMapping(ApiConstants.Candidate.GET)
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.CANDIDATE_FETCH_SUCCESS, candidateService.getById(id)));
    }
}
