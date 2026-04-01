package com.vietrecruit.feature.user.service;

import org.springframework.web.multipart.MultipartFile;

import com.vietrecruit.feature.user.dto.request.UpdateProfileRequest;
import com.vietrecruit.feature.user.dto.response.AvatarUploadResponse;
import com.vietrecruit.feature.user.dto.response.BannerUploadResponse;
import com.vietrecruit.feature.user.dto.response.UserProfileResponse;

public interface ClientUserService {

    /**
     * Returns the profile of the currently authenticated user.
     *
     * @return the user profile response
     */
    UserProfileResponse getProfile();

    /**
     * Updates the mutable profile fields of the currently authenticated user.
     *
     * @param request updated profile fields including display name and contact info
     * @return the updated user profile response
     */
    UserProfileResponse updateProfile(UpdateProfileRequest request);

    /**
     * Uploads an avatar image to storage and sets it as the user's current avatar.
     *
     * @param file the multipart image file (JPEG, PNG, or WebP)
     * @return upload result including the public avatar URL
     */
    AvatarUploadResponse uploadAvatar(MultipartFile file);

    /**
     * Sets the user's avatar to an externally hosted image URL.
     *
     * @param externalUrl the fully-qualified external image URL
     */
    void setAvatarUrl(String externalUrl);

    /**
     * Removes the user's current avatar and deletes the associated storage object if applicable.
     */
    void deleteAvatar();

    /**
     * Uploads a banner image to storage and sets it as the user's current profile banner.
     *
     * @param file the multipart image file (JPEG, PNG, or WebP)
     * @return upload result including the public banner URL
     */
    BannerUploadResponse uploadBanner(MultipartFile file);

    /**
     * Sets the user's profile banner to an externally hosted image URL.
     *
     * @param externalUrl the fully-qualified external image URL
     */
    void setBannerUrl(String externalUrl);

    /**
     * Removes the user's current profile banner and deletes the associated storage object if
     * applicable.
     */
    void deleteBanner();
}
