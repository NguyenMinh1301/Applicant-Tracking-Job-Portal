package com.vietrecruit.feature.user.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import com.vietrecruit.feature.user.dto.request.UserRequest;
import com.vietrecruit.feature.user.dto.response.AdminUserResponse;
import com.vietrecruit.feature.user.service.AdminUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.AdminUser.ROOT)
@Tag(name = "Admin User Service", description = "Administrative endpoints for managing users")
public class AdminUserController extends BaseController {

    private final AdminUserService adminUserService;

    @Operation(summary = "Create User", description = "Creates a new user (Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "User created successfully")
    @PreAuthorize("hasAuthority('USER:MANAGE')")
    @PostMapping(ApiConstants.AdminUser.CREATE)
    public ResponseEntity<ApiResponse<AdminUserResponse>> create(
            @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.USER_CREATE_SUCCESS, adminUserService.create(request)));
    }

    @Operation(summary = "Get User", description = "Retrieves user details by ID (Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "User retrieved successfully")
    @PreAuthorize("hasAuthority('USER:MANAGE')")
    @GetMapping(ApiConstants.AdminUser.GET)
    public ResponseEntity<ApiResponse<AdminUserResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.USER_FETCH_SUCCESS, adminUserService.get(id)));
    }

    @Operation(
            summary = "List Users",
            description = "Retrieves a paginated list of users (Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Users listed successfully")
    @PreAuthorize("hasAuthority('USER:MANAGE')")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.USER_LIST_SUCCESS,
                        PageResponse.from(adminUserService.list(pageable))));
    }

    @Operation(
            summary = "Update User",
            description = "Updates an existing user's details (Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "User updated successfully")
    @PreAuthorize("hasAuthority('USER:MANAGE')")
    @PutMapping(ApiConstants.AdminUser.UPDATE)
    public ResponseEntity<ApiResponse<AdminUserResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.USER_UPDATE_SUCCESS, adminUserService.update(id, request)));
    }

    @Operation(summary = "Delete User", description = "Deletes a user by ID (Admin only)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "User deleted successfully")
    @PreAuthorize("hasAuthority('USER:DELETE')")
    @DeleteMapping(ApiConstants.AdminUser.DELETE)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        adminUserService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.USER_DELETE_SUCCESS));
    }
}
