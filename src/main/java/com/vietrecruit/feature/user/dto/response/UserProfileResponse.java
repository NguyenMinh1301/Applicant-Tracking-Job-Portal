package com.vietrecruit.feature.user.dto.response;

import java.time.LocalDate;
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
@Schema(description = "User profile response for client views")
public class UserProfileResponse {

    @Schema(description = "User's unique identifier")
    private UUID id;

    @Schema(description = "User's email address")
    private String email;

    @Schema(description = "User's full name")
    private String fullName;

    @Schema(description = "User's phone number")
    private String phone;

    @Schema(description = "URL to the user's avatar image")
    private String avatarUrl;

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
}
