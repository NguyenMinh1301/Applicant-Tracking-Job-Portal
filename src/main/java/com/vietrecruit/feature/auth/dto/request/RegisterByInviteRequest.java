package com.vietrecruit.feature.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
@Schema(description = "Payload for registering via an invitation token")
public class RegisterByInviteRequest {

    @Schema(
            description = "Invitation token from the invite link",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Token is required")
    private String token;

    @Schema(
            description = "User's password (min 8 chars)",
            example = "SecurePass123!",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,72}$",
            message = "Password must contain uppercase, lowercase, digit, and special character")
    private String password;

    @Schema(
            description = "User's full name",
            example = "Jane Doe",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;
}
