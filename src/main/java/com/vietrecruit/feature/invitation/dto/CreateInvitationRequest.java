package com.vietrecruit.feature.invitation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

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
@Schema(description = "Payload for creating an invitation for HR or INTERVIEWER")
public class CreateInvitationRequest {

    @Schema(
            description = "Email address of the invited user",
            example = "hr@company.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Schema(
            description = "Role to assign: HR or INTERVIEWER",
            example = "HR",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Role is required")
    private String role;
}
