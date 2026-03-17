package com.vietrecruit.feature.ai.interview.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.feature.ai.interview.dto.InterviewQuestionResponse;
import com.vietrecruit.feature.ai.interview.service.InterviewQuestionService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "AI - Interview Questions",
        description = "AI-generated interview questions tailored to the candidate and job")
public class InterviewQuestionController extends BaseController {

    private final InterviewQuestionService interviewQuestionService;

    @Operation(
            summary = "Generate Interview Questions",
            description =
                    "Generates a structured set of 10 interview questions tailored to the"
                            + " candidate's CV and job requirements. Idempotent — returns stored"
                            + " questions if already generated.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN', 'ROLE_INTERVIEWER')")
    @PostMapping(ApiConstants.Interview.ROOT + ApiConstants.Interview.QUESTIONS_GENERATE)
    public ResponseEntity<ApiResponse<InterviewQuestionResponse>> generateQuestions(
            @PathVariable UUID id) {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.INTERVIEW_QUESTIONS_GENERATED,
                        interviewQuestionService.generate(id, companyId)));
    }

    @Operation(
            summary = "Get Interview Questions",
            description =
                    "Returns the previously generated question set for an interview. Returns 404"
                            + " if questions have not yet been generated.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN', 'ROLE_INTERVIEWER')")
    @GetMapping(ApiConstants.Interview.ROOT + ApiConstants.Interview.QUESTIONS)
    public ResponseEntity<ApiResponse<InterviewQuestionResponse>> getQuestions(
            @PathVariable UUID id) {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.INTERVIEW_QUESTIONS_FETCH_SUCCESS,
                        interviewQuestionService.getQuestions(id, companyId)));
    }
}
