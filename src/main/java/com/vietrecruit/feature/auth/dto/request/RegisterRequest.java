package com.vietrecruit.feature.auth.dto.request;

import jakarta.validation.constraints.Email;
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
@Schema(description = "Payload for user registration")
public class RegisterRequest {

    @Schema(
            description = "User's email address",
            example = "newuser@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

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
            example = "John Doe",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    @Schema(description = "User's phone number", example = "+1234567890")
    @Size(max = 50, message = "Phone must not exceed 50 characters")
    private String phone;

    // Intentional: employer self-registration is a supported flow (creates company shell)
    @Schema(
            description = "Account type: CANDIDATE (default) or EMPLOYER",
            example = "CANDIDATE",
            allowableValues = {"CANDIDATE", "EMPLOYER"},
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @Pattern(
            regexp = "^(CANDIDATE|EMPLOYER)$",
            message = "Account type must be CANDIDATE or EMPLOYER")
    private String accountType;
}
