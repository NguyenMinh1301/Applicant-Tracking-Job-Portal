package com.vietrecruit.feature.company.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyResponse {
    private UUID id;
    private String name;
    private String domain;
    private String website;
    private Instant createdAt;
    private Instant updatedAt;
}
