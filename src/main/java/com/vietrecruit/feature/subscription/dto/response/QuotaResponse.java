package com.vietrecruit.feature.subscription.dto.response;

import java.time.Instant;

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
public class QuotaResponse {
    private Integer maxActiveJobs;
    private Integer jobsActive;
    private Integer jobsPosted;
    private Instant cycleStart;
    private Instant cycleEnd;
}
