package com.vietrecruit.feature.department.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentRequest {

    @NotBlank(message = "Department name is required")
    @Size(max = 255, message = "Department name must not exceed 255 characters")
    private String name;

    private String description;
}
