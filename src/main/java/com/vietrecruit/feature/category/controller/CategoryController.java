package com.vietrecruit.feature.category.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageImpl;
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
import com.vietrecruit.common.response.PageResponse;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.feature.category.dto.request.CategoryRequest;
import com.vietrecruit.feature.category.dto.response.CategoryResponse;
import com.vietrecruit.feature.category.service.CategoryService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Category.ROOT)
@Tag(name = "Category", description = "Endpoints for managing company categories")
public class CategoryController extends BaseController {

    private final CategoryService categoryService;

    @Operation(summary = "Create Category")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR')")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CategoryRequest request) {
        var companyId = resolveCompanyId();
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                ApiSuccessCode.CATEGORY_CREATE_SUCCESS,
                                categoryService.createCategory(companyId, userId, request)));
    }

    @Operation(summary = "List Categories")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR')")
    @Parameters({
        @Parameter(name = "page", description = "Page number (0-based)", example = "0"),
        @Parameter(name = "size", description = "Page size", example = "20"),
        @Parameter(name = "sort", description = "Sort field and direction", example = "name,asc")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CategoryResponse>>> list(
            @ParameterObject
                    @PageableDefault(
                            page = 0,
                            size = 20,
                            sort = "name",
                            direction = Sort.Direction.ASC)
                    Pageable pageable) {
        var companyId = resolveCompanyId();
        var all = categoryService.listCategories(companyId);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        var page =
                new PageImpl<>(
                        start >= all.size() ? java.util.List.of() : all.subList(start, end),
                        pageable,
                        all.size());
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.CATEGORY_LIST_SUCCESS, PageResponse.from(page)));
    }

    @Operation(summary = "Get Category")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR')")
    @GetMapping(ApiConstants.Category.GET)
    public ResponseEntity<ApiResponse<CategoryResponse>> get(@PathVariable UUID id) {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.CATEGORY_FETCH_SUCCESS,
                        categoryService.getCategory(companyId, id)));
    }

    @Operation(summary = "Update Category")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR')")
    @PutMapping(ApiConstants.Category.UPDATE)
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        var companyId = resolveCompanyId();
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.CATEGORY_UPDATE_SUCCESS,
                        categoryService.updateCategory(companyId, id, userId, request)));
    }

    @Operation(summary = "Delete Category")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR')")
    @DeleteMapping(ApiConstants.Category.DELETE)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        var companyId = resolveCompanyId();
        categoryService.deleteCategory(companyId, id);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.CATEGORY_DELETE_SUCCESS, null));
    }
}
