package com.vietrecruit.feature.user.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

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
@Schema(description = "Payload for updating user profile")
public class UpdateProfileRequest {

    @Schema(description = "User's full name", example = "John Doe")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    @Schema(description = "User's phone number", example = "+1234567890")
    @Size(max = 50, message = "Phone must not exceed 50 characters")
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

    @Schema(description = "User's gender", example = "MALE")
    private String gender;
}
