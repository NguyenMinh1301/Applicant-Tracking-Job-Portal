package com.vietrecruit.feature.user.service;

import org.springframework.web.multipart.MultipartFile;

import com.vietrecruit.feature.user.dto.request.UpdateProfileRequest;
import com.vietrecruit.feature.user.dto.response.AvatarUploadResponse;
import com.vietrecruit.feature.user.dto.response.BannerUploadResponse;
import com.vietrecruit.feature.user.dto.response.UserProfileResponse;

public interface ClientUserService {
    UserProfileResponse getProfile();

    UserProfileResponse updateProfile(UpdateProfileRequest request);

    AvatarUploadResponse uploadAvatar(MultipartFile file);

    void setAvatarUrl(String externalUrl);

    void deleteAvatar();

    BannerUploadResponse uploadBanner(MultipartFile file);

    void setBannerUrl(String externalUrl);

    void deleteBanner();
}
