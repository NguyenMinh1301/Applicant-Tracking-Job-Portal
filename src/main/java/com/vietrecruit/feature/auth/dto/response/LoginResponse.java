package com.vietrecruit.feature.auth.dto.response;

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
@Schema(description = "Response payload containing the JWT tokens upon successful login")
public class LoginResponse {

    @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiIsInR...")
    private String accessToken;

    @Schema(description = "JWT Refresh Token", example = "d73f4e91-e0c1-4b1d-8f1...")
    private String refreshToken;

    @Schema(description = "Token expiration time in seconds", example = "3600")
    private long expiresIn;

    @Schema(description = "Type of the token", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";
}
