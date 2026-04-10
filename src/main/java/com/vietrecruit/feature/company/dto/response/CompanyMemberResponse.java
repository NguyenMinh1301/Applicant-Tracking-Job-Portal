package com.vietrecruit.feature.company.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Company member information")
public class CompanyMemberResponse {

    @Schema(description = "User ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Full name", example = "Nguyễn Văn A")
    private String fullName;

    @Schema(description = "Email address", example = "nguyen.van.a@company.com")
    private String email;

    @Schema(description = "User role", example = "HR")
    private String role;

    @Schema(description = "Avatar URL")
    private String avatarUrl;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;

    @Schema(description = "Whether the account is active", example = "true")
    private Boolean active;
}
