package com.vietrecruit.feature.user.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.vietrecruit.feature.user.dto.request.ExternalUrlRequest;
import com.vietrecruit.feature.user.dto.request.UpdateProfileRequest;
import com.vietrecruit.feature.user.dto.response.AvatarUploadResponse;
import com.vietrecruit.feature.user.dto.response.BannerUploadResponse;
import com.vietrecruit.feature.user.dto.response.UserProfileResponse;
import com.vietrecruit.feature.user.service.ClientUserService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.ClientUser.ROOT)
@Tag(name = "Client User Service", description = "Endpoints for user's own profile management")
public class ClientUserController extends BaseController {

    private final ClientUserService clientUserService;

    @Operation(
            summary = "Get Profile",
            description = "Retrieves the profile of the currently authenticated user")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Profile retrieved successfully")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @GetMapping(ApiConstants.ClientUser.ME)
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.USER_FETCH_SUCCESS, clientUserService.getProfile()));
    }

    @Operation(
            summary = "Update Profile",
            description = "Updates the profile of the currently authenticated user")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Profile updated successfully")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PutMapping(ApiConstants.ClientUser.ME)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.USER_UPDATE_SUCCESS,
                        clientUserService.updateProfile(request)));
    }

    // --- Avatar endpoints ---

    @Operation(
            summary = "Upload Avatar",
            description = "Uploads a profile avatar image. Accepted: JPEG, PNG, WebP. Max 2MB.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.ClientUser.ME_AVATAR)
    public ResponseEntity<ApiResponse<AvatarUploadResponse>> uploadAvatar(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.USER_AVATAR_UPLOAD_SUCCESS,
                        clientUserService.uploadAvatar(file)));
    }

    @Operation(
            summary = "Set Avatar URL",
            description = "Sets an external URL as the user's avatar (must be HTTPS)")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PutMapping(ApiConstants.ClientUser.ME_AVATAR_URL)
    public ResponseEntity<ApiResponse<Void>> setAvatarUrl(
            @Valid @RequestBody ExternalUrlRequest request) {
        clientUserService.setAvatarUrl(request.getUrl());
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.USER_AVATAR_UPDATE_SUCCESS));
    }

    @Operation(
            summary = "Delete Avatar",
            description = "Removes the user's avatar and reverts to default")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @DeleteMapping(ApiConstants.ClientUser.ME_AVATAR)
    public ResponseEntity<ApiResponse<Void>> deleteAvatar() {
        clientUserService.deleteAvatar();
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.USER_AVATAR_DELETE_SUCCESS));
    }

    // --- Banner endpoints ---

    @Operation(
            summary = "Upload Banner",
            description = "Uploads a profile banner image. Accepted: JPEG, PNG, WebP. Max 3MB.")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PostMapping(ApiConstants.ClientUser.ME_BANNER)
    public ResponseEntity<ApiResponse<BannerUploadResponse>> uploadBanner(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        ApiSuccessCode.USER_BANNER_UPLOAD_SUCCESS,
                        clientUserService.uploadBanner(file)));
    }

    @Operation(
            summary = "Set Banner URL",
            description = "Sets an external URL as the user's banner (must be HTTPS)")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @PutMapping(ApiConstants.ClientUser.ME_BANNER_URL)
    public ResponseEntity<ApiResponse<Void>> setBannerUrl(
            @Valid @RequestBody ExternalUrlRequest request) {
        clientUserService.setBannerUrl(request.getUrl());
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.USER_BANNER_UPDATE_SUCCESS));
    }

    @Operation(
            summary = "Delete Banner",
            description = "Removes the user's banner and reverts to default")
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    @DeleteMapping(ApiConstants.ClientUser.ME_BANNER)
    public ResponseEntity<ApiResponse<Void>> deleteBanner() {
        clientUserService.deleteBanner();
        return ResponseEntity.ok(ApiResponse.success(ApiSuccessCode.USER_BANNER_DELETE_SUCCESS));
    }
}
