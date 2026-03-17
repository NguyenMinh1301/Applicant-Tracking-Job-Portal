package com.vietrecruit.feature.ai.salary.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.feature.ai.salary.dto.SalaryBenchmarkResponse;
import com.vietrecruit.feature.ai.salary.service.SalaryBenchmarkService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(
        name = "AI - Salary Benchmark",
        description = "AI-powered salary benchmark for candidates and employers")
public class SalaryBenchmarkController extends BaseController {

    private final SalaryBenchmarkService salaryBenchmarkService;

    @Operation(
            summary = "Candidate salary benchmark",
            description =
                    "Estimates salary range for a given job title based on internal market data"
                            + " and salary survey knowledge. Results cached 24 hours.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasRole('CANDIDATE')")
    @GetMapping(ApiConstants.Candidate.ROOT + ApiConstants.Candidate.ME_SALARY_BENCHMARK)
    public ResponseEntity<ApiResponse<SalaryBenchmarkResponse>> candidateSalaryBenchmark(
            @RequestParam String jobTitle, @RequestParam(required = false) UUID locationId) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.SALARY_BENCHMARK_SUCCESS,
                        salaryBenchmarkService.benchmarkForCandidate(jobTitle, locationId)));
    }

    @Operation(
            summary = "Job salary benchmark",
            description =
                    "Estimates salary competitiveness for a specific job posting based on internal"
                            + " market data and salary survey knowledge. Results cached 24 hours.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasRole('HR') or hasRole('COMPANY_ADMIN')")
    @GetMapping(ApiConstants.Job.ROOT + ApiConstants.Job.SALARY_BENCHMARK)
    public ResponseEntity<ApiResponse<SalaryBenchmarkResponse>> jobSalaryBenchmark(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.SALARY_BENCHMARK_SUCCESS,
                        salaryBenchmarkService.benchmarkForJob(id)));
    }
}
