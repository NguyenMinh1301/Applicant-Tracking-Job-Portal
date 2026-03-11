package com.vietrecruit.feature.user.service.impl;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.common.security.SecurityUtils;
import com.vietrecruit.common.storage.StorageService;
import com.vietrecruit.feature.user.dto.request.UpdateProfileRequest;
import com.vietrecruit.feature.user.dto.response.AvatarUploadResponse;
import com.vietrecruit.feature.user.dto.response.BannerUploadResponse;
import com.vietrecruit.feature.user.dto.response.UserProfileResponse;
import com.vietrecruit.feature.user.entity.User;
import com.vietrecruit.feature.user.mapper.UserMapper;
import com.vietrecruit.feature.user.repository.UserRepository;
import com.vietrecruit.feature.user.service.ClientUserService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientUserServiceImpl implements ClientUserService {

    private static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024 * 1024; // 2MB
    private static final long MAX_BANNER_SIZE_BYTES = 3L * 1024 * 1024; // 3MB

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final StorageService storageService;

    @Override
    public UserProfileResponse getProfile() {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user =
                userRepository
                        .findByIdWithRolesAndPermissions(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));

        userMapper.updateProfile(user, request);
        user = userRepository.save(user);
        return userMapper.toProfileResponse(user);
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "r2Storage", fallbackMethod = "uploadAvatarFallback")
    @Retry(name = "r2Upload")
    public AvatarUploadResponse uploadAvatar(MultipartFile file) {
        validateImageFile(
                file,
                MAX_AVATAR_SIZE_BYTES,
                ApiErrorCode.USER_AVATAR_INVALID_TYPE,
                ApiErrorCode.USER_AVATAR_SIZE_EXCEEDED);

        UUID userId = SecurityUtils.getCurrentUserId();
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));

        // Delete old avatar from R2 if exists
        deleteR2Object(user.getAvatarObjectKey());

        String ext = extractExtension(file.getOriginalFilename());
        String objectKey = String.format("users/%s/avatar/%s.%s", userId, UUID.randomUUID(), ext);

        String publicUrl = doUpload(objectKey, file);

        user.setAvatarUrl(publicUrl);
        user.setAvatarObjectKey(objectKey);
        userRepository.save(user);

        return AvatarUploadResponse.builder()
                .avatarUrl(publicUrl)
                .uploadedAt(Instant.now())
                .build();
    }

    @Override
    @Transactional
    public void setAvatarUrl(String externalUrl) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));

        // Delete old R2 avatar if existed
        deleteR2Object(user.getAvatarObjectKey());

        user.setAvatarUrl(externalUrl);
        user.setAvatarObjectKey(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "r2Storage", fallbackMethod = "deleteAvatarFallback")
    public void deleteAvatar() {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));

        deleteR2Object(user.getAvatarObjectKey());

        user.setAvatarUrl(null);
        user.setAvatarObjectKey(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "r2Storage", fallbackMethod = "uploadBannerFallback")
    @Retry(name = "r2Upload")
    public BannerUploadResponse uploadBanner(MultipartFile file) {
        validateImageFile(
                file,
                MAX_BANNER_SIZE_BYTES,
                ApiErrorCode.USER_BANNER_INVALID_TYPE,
                ApiErrorCode.USER_BANNER_SIZE_EXCEEDED);

        UUID userId = SecurityUtils.getCurrentUserId();
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));

        // Delete old banner from R2 if exists
        deleteR2Object(user.getBannerObjectKey());

        String ext = extractExtension(file.getOriginalFilename());
        String objectKey = String.format("users/%s/banner/%s.%s", userId, UUID.randomUUID(), ext);

        String publicUrl = doUpload(objectKey, file);

        user.setBannerUrl(publicUrl);
        user.setBannerObjectKey(objectKey);
        userRepository.save(user);

        return BannerUploadResponse.builder()
                .bannerUrl(publicUrl)
                .uploadedAt(Instant.now())
                .build();
    }

    @Override
    @Transactional
    public void setBannerUrl(String externalUrl) {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));

        deleteR2Object(user.getBannerObjectKey());

        user.setBannerUrl(externalUrl);
        user.setBannerObjectKey(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    @CircuitBreaker(name = "r2Storage", fallbackMethod = "deleteBannerFallback")
    public void deleteBanner() {
        UUID userId = SecurityUtils.getCurrentUserId();
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));

        deleteR2Object(user.getBannerObjectKey());

        user.setBannerUrl(null);
        user.setBannerObjectKey(null);
        userRepository.save(user);
    }

    // --- Private helpers ---

    private void validateImageFile(
            MultipartFile file,
            long maxSize,
            ApiErrorCode invalidTypeCode,
            ApiErrorCode sizeExceededCode) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new ApiException(invalidTypeCode);
        }
        if (file.getSize() > maxSize) {
            throw new ApiException(sizeExceededCode);
        }
    }

    private String doUpload(String objectKey, MultipartFile file) {
        try {
            return storageService.upload(
                    objectKey, file.getInputStream(), file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to read upload file", e);
        }
    }

    private void deleteR2Object(String objectKey) {
        if (objectKey != null) {
            storageService.delete(objectKey);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    // --- Resilience4j fallback methods ---

    @SuppressWarnings("unused")
    private AvatarUploadResponse uploadAvatarFallback(MultipartFile file, Throwable t) {
        log.error("R2 avatar upload circuit breaker triggered: {}", t.getMessage());
        throw new ApiException(ApiErrorCode.STORAGE_UNAVAILABLE);
    }

    @SuppressWarnings("unused")
    private void deleteAvatarFallback(Throwable t) {
        log.warn("R2 avatar delete circuit breaker triggered: {}", t.getMessage());
    }

    @SuppressWarnings("unused")
    private BannerUploadResponse uploadBannerFallback(MultipartFile file, Throwable t) {
        log.error("R2 banner upload circuit breaker triggered: {}", t.getMessage());
        throw new ApiException(ApiErrorCode.STORAGE_UNAVAILABLE);
    }

    @SuppressWarnings("unused")
    private void deleteBannerFallback(Throwable t) {
        log.warn("R2 banner delete circuit breaker triggered: {}", t.getMessage());
    }
}
