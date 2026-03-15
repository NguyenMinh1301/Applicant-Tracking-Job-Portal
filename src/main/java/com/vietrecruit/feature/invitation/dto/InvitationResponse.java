package com.vietrecruit.feature.invitation.dto;

import java.time.Instant;
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
@Schema(description = "Response payload for a created invitation")
public class InvitationResponse {

    @Schema(description = "Invitation unique identifier")
    private UUID invitationId;

    @Schema(description = "Invitation expiration timestamp")
    private Instant expiresAt;
}
