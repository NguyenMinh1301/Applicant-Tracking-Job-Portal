package com.vietrecruit.feature.department.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DepartmentResponse {
    private UUID id;
    private String name;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
