package com.vietrecruit.feature.company.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.vietrecruit.common.response.SearchPageResponse;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.company.dto.request.CompanyCreateRequest;
import com.vietrecruit.feature.company.dto.request.CompanySearchRequest;
import com.vietrecruit.feature.company.dto.request.CompanyUpdateRequest;
import com.vietrecruit.feature.company.dto.response.CompanyResponse;
import com.vietrecruit.feature.company.dto.response.CompanySearchResponse;
import com.vietrecruit.feature.company.service.CompanySearchService;
import com.vietrecruit.feature.company.service.CompanyService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Company.ROOT)
@Tag(name = "Company", description = "Endpoints for managing own company profile")
public class CompanyController extends BaseController {

    private final CompanyService companyService;
    private final CompanySearchService companySearchService;

    @Operation(
            summary = "Search Companies",
            description = "Full-text search across company profiles (public)")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.Company.SEARCH)
    public ResponseEntity<ApiResponse<SearchPageResponse<CompanySearchResponse>>> searchCompanies(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var request = CompanySearchRequest.builder().q(q).page(page).size(size).build();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.SEARCH_SUCCESS, companySearchService.search(request)));
    }

    @Operation(
            summary = "Create Company",
            description =
                    "Creates a new company and associates it with the authenticated user."
                            + " Only for COMPANY_ADMIN users without an existing company.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.Company.CREATE)
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @Valid @RequestBody CompanyCreateRequest request) {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                ApiSuccessCode.COMPANY_CREATE_SUCCESS,
                                companyService.createCompany(userId, request)));
    }

    @Operation(
            summary = "Get Company",
            description = "Retrieves the authenticated user's company profile")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.Company.ME)
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany() {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.COMPANY_FETCH_SUCCESS,
                        companyService.getCompany(companyId)));
    }

    @Operation(
            summary = "Update Company",
            description = "Updates the authenticated user's company profile")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PutMapping(ApiConstants.Company.ME)
    public ResponseEntity<ApiResponse<CompanyResponse>> updateCompany(
            @Valid @RequestBody CompanyUpdateRequest request) {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.COMPANY_UPDATE_SUCCESS,
                        companyService.updateCompany(companyId, request)));
    }
}
