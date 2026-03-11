package com.vietrecruit.feature.user.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed user response for admin views")
public class AdminUserResponse {

    @Schema(description = "User's unique identifier")
    private UUID id;

    @Schema(description = "Company ID associated with the user")
    private UUID companyId;

    @Schema(description = "User's registered email address")
    private String email;

    @Schema(description = "User's full name")
    private String fullName;

    @Schema(description = "User's phone number")
    private String phone;

    @Schema(description = "URL to the user's avatar image")
    private String avatarUrl;

    @Schema(description = "URL to the user's banner image")
    private String bannerUrl;

    @Schema(description = "LinkedIn profile URL")
    private String linkedinUrl;

    @Schema(description = "GitHub profile URL")
    private String githubUrl;

    @Schema(description = "Portfolio website URL")
    private String portfolioUrl;

    @Schema(description = "User's physical location or address")
    private String location;

    @Schema(description = "Date of birth")
    private LocalDate dob;

    @Schema(description = "User's gender")
    private String gender;

    @Schema(description = "Whether the user account is active")
    private Boolean isActive;

    @Schema(description = "Whether the user account is locked")
    private Boolean isLocked;

    @Schema(description = "Number of failed login attempts")
    private Short failedAttempts;

    @Schema(description = "Account lock expiration time")
    private Instant lockUntil;

    @Schema(description = "Last login timestamp")
    private Instant lastLoginAt;

    @Schema(description = "Set of roles assigned to the user")
    private Set<String> roles;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;

    @Schema(description = "Account last update timestamp")
    private Instant updatedAt;
}
