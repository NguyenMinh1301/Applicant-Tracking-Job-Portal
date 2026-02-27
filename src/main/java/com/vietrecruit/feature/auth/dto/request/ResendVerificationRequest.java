package com.vietrecruit.feature.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload for resending verification email")
public class ResendVerificationRequest {

    @Schema(
            description = "User's email address to resend the verification to",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @Email
    @NotBlank
    private String email;
}
