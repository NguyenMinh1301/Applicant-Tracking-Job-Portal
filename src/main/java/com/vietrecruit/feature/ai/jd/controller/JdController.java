package com.vietrecruit.feature.ai.jd.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.feature.ai.jd.dto.ApplyDescriptionRequest;
import com.vietrecruit.feature.ai.jd.dto.JdGenerateRequest;
import com.vietrecruit.feature.ai.jd.dto.JdGenerateResponse;
import com.vietrecruit.feature.ai.jd.service.JdGeneratorService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "AI - Job Description Generator",
        description = "AI-powered job description generation with inclusive language enforcement")
public class JdController extends BaseController {

    private final JdGeneratorService jdGeneratorService;

    @Operation(
            summary = "Generate Job Description",
            description =
                    "Generates a complete, bias-free job description from minimal inputs using"
                            + " gpt-4o. Does not auto-save — employer reviews and applies"
                            + " separately.")
    @RateLimiter(name = "jdGenerator", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @PostMapping(ApiConstants.Job.ROOT + ApiConstants.Job.AI_GENERATE_DESCRIPTION)
    public ResponseEntity<ApiResponse<JdGenerateResponse>> generateDescription(
            @Valid @RequestBody JdGenerateRequest request) {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.JD_GENERATION_SUCCESS,
                        jdGeneratorService.generate(request, companyId)));
    }

    @Operation(
            summary = "Apply Generated Description to Job",
            description =
                    "Overwrites the job's description field with the previously generated"
                            + " content. No AI call is made.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @PostMapping(ApiConstants.Job.ROOT + ApiConstants.Job.AI_APPLY_DESCRIPTION)
    public ResponseEntity<ApiResponse<Void>> applyDescription(
            @PathVariable UUID id, @Valid @RequestBody ApplyDescriptionRequest request) {
        var companyId = resolveCompanyId();
        jdGeneratorService.applyDescription(id, companyId, request);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.JD_APPLY_SUCCESS));
    }
}
