package com.vietrecruit.feature.company.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyCreateRequest {

    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name must not exceed 255 characters")
    private String name;

    @Size(max = 255, message = "Domain must not exceed 255 characters")
    private String domain;

    @Size(max = 255, message = "Website must not exceed 255 characters")
    private String website;
}
