package com.vietrecruit.feature.subscription.dto.response;

import java.time.Instant;
import java.util.UUID;

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
public class SubscriptionResponse {
    private UUID id;
    private String planName;
    private String planCode;
    private String status;
    private Instant startedAt;
    private Instant expiresAt;
    private Boolean autoRenew;
}
