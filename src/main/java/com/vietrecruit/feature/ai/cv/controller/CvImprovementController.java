package com.vietrecruit.feature.ai.cv.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.ai.cv.dto.CvImprovementResponse;
import com.vietrecruit.feature.ai.cv.service.CvImprovementService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Candidate.ROOT)
@Tag(
        name = "AI - CV Improvement",
        description = "AI-powered CV analysis and improvement suggestions")
public class CvImprovementController extends BaseController {

    private final CvImprovementService cvImprovementService;

    @Operation(
            summary = "Analyze CV",
            description =
                    "Analyzes the authenticated candidate's uploaded CV and returns prioritized"
                            + " improvement suggestions based on their target roles and CV writing"
                            + " best practices. Results are cached for 6 hours.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasRole('CANDIDATE')")
    @PostMapping(ApiConstants.Candidate.ME_CV_IMPROVEMENT)
    public ResponseEntity<ApiResponse<CvImprovementResponse>> analyzeCv() {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.CV_IMPROVEMENT_SUCCESS,
                        cvImprovementService.analyze(userId)));
    }
}
