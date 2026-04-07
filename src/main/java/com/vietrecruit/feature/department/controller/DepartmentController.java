package com.vietrecruit.feature.department.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import com.vietrecruit.common.ApiConstants;
import com.vietrecruit.common.base.BaseController;
import com.vietrecruit.common.enums.ApiSuccessCode;
import com.vietrecruit.common.response.ApiResponse;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.department.dto.request.DepartmentRequest;
import com.vietrecruit.feature.department.dto.response.DepartmentResponse;
import com.vietrecruit.feature.department.service.DepartmentService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Department.ROOT)
@Tag(name = "Department", description = "Endpoints for managing company departments")
public class DepartmentController extends BaseController {

    private final DepartmentService departmentService;

    @Operation(summary = "Create Department")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(
            @Valid @RequestBody DepartmentRequest request) {
        var companyId = resolveCompanyId();
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                ApiSuccessCode.DEPARTMENT_CREATE_SUCCESS,
                                departmentService.createDepartment(companyId, userId, request)));
    }

    @Operation(summary = "List Departments")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR')")
    @Parameters({
        @Parameter(name = "page", description = "Page number (0-based)", example = "0"),
        @Parameter(name = "size", description = "Page size", example = "20"),
        @Parameter(name = "sort", description = "Sort field and direction", example = "name,asc")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<DepartmentResponse>>> list(
            @ParameterObject
                    @PageableDefault(
                            page = 0,
                            size = 20,
                            sort = "name",
                            direction = Sort.Direction.ASC)
                    Pageable pageable) {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.DEPARTMENT_LIST_SUCCESS,
                        departmentService.listDepartments(companyId, pageable)));
    }

    @Operation(summary = "Get Department")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR')")
    @GetMapping(ApiConstants.Department.GET)
    public ResponseEntity<ApiResponse<DepartmentResponse>> get(@PathVariable UUID id) {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.DEPARTMENT_FETCH_SUCCESS,
                        departmentService.getDepartment(companyId, id)));
    }

    @Operation(summary = "Update Department")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @PutMapping(ApiConstants.Department.UPDATE)
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody DepartmentRequest request) {
        var companyId = resolveCompanyId();
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.DEPARTMENT_UPDATE_SUCCESS,
                        departmentService.updateDepartment(companyId, id, userId, request)));
    }

    @Operation(summary = "Delete Department")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyAuthority('ROLE_HR', 'ROLE_COMPANY_ADMIN')")
    @DeleteMapping(ApiConstants.Department.DELETE)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        var companyId = resolveCompanyId();
        departmentService.deleteDepartment(companyId, id);
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.DEPARTMENT_DELETE_SUCCESS, null));
    }
}
