package com.vietrecruit.feature.location.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import com.vietrecruit.feature.location.dto.request.LocationRequest;
import com.vietrecruit.feature.location.dto.response.LocationResponse;
import com.vietrecruit.feature.location.service.LocationService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.Location.ROOT)
@Tag(name = "Location", description = "Endpoints for managing company locations")
public class LocationController extends BaseController {

    private final LocationService locationService;

    @Operation(summary = "Create Location")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PostMapping
    public ResponseEntity<ApiResponse<LocationResponse>> create(
            @Valid @RequestBody LocationRequest request) {
        var companyId = resolveCompanyId();
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                ApiSuccessCode.LOCATION_CREATE_SUCCESS,
                                locationService.createLocation(companyId, userId, request)));
    }

    @Operation(summary = "List Locations")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @Parameters({
        @Parameter(name = "page", description = "Page number (0-based)", example = "0"),
        @Parameter(name = "size", description = "Page size", example = "20"),
        @Parameter(name = "sort", description = "Sort field and direction", example = "name,asc")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<Page<LocationResponse>>> list(
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
                        ApiSuccessCode.LOCATION_LIST_SUCCESS,
                        locationService.listLocations(companyId, pageable)));
    }

    @Operation(summary = "Get Location")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.Location.GET)
    public ResponseEntity<ApiResponse<LocationResponse>> get(@PathVariable UUID id) {
        var companyId = resolveCompanyId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.LOCATION_FETCH_SUCCESS,
                        locationService.getLocation(companyId, id)));
    }

    @Operation(summary = "Update Location")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PutMapping(ApiConstants.Location.UPDATE)
    public ResponseEntity<ApiResponse<LocationResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody LocationRequest request) {
        var companyId = resolveCompanyId();
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.LOCATION_UPDATE_SUCCESS,
                        locationService.updateLocation(companyId, id, userId, request)));
    }

    @Operation(summary = "Delete Location")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @DeleteMapping(ApiConstants.Location.DELETE)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        var companyId = resolveCompanyId();
        locationService.deleteLocation(companyId, id);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.LOCATION_DELETE_SUCCESS, null));
    }
}
