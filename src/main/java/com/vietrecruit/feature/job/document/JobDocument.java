package com.vietrecruit.feature.job.document;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobDocument {

    private String id;
    private String title;
    private String description;
    private String requirements;
    private String status;

    @JsonProperty("company_id")
    private String companyId;

    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("category_id")
    private String categoryId;

    @JsonProperty("category_name")
    private String categoryName;

    @JsonProperty("location_id")
    private String locationId;

    @JsonProperty("location_name")
    private String locationName;

    @JsonProperty("min_salary")
    private Double minSalary;

    @JsonProperty("max_salary")
    private Double maxSalary;

    private String currency;

    @JsonProperty("is_negotiable")
    private Boolean isNegotiable;

    private String deadline;

    @JsonProperty("public_link")
    private String publicLink;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public static JobDocument fromCdcFields(
            UUID id,
            String title,
            String description,
            String requirements,
            String status,
            UUID companyId,
            String companyName,
            UUID categoryId,
            String categoryName,
            UUID locationId,
            String locationName,
            Double minSalary,
            Double maxSalary,
            String currency,
            Boolean isNegotiable,
            String deadline,
            String publicLink,
            Instant createdAt,
            Instant updatedAt) {
        return JobDocument.builder()
                .id(id != null ? id.toString() : null)
                .title(title)
                .description(description)
                .requirements(requirements)
                .status(status)
                .companyId(companyId != null ? companyId.toString() : null)
                .companyName(companyName)
                .categoryId(categoryId != null ? categoryId.toString() : null)
                .categoryName(categoryName)
                .locationId(locationId != null ? locationId.toString() : null)
                .locationName(locationName)
                .minSalary(minSalary)
                .maxSalary(maxSalary)
                .currency(currency)
                .isNegotiable(isNegotiable)
                .deadline(deadline)
                .publicLink(publicLink)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
