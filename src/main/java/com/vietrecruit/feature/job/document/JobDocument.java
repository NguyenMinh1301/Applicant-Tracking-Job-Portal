package com.vietrecruit.feature.job.document;

import java.time.Instant;

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

    @JsonProperty("view_count")
    private Integer viewCount;

    @JsonProperty("application_count")
    private Integer applicationCount;

    @JsonProperty("is_hot")
    private Boolean isHot;

    @JsonProperty("is_featured")
    private Boolean isFeatured;

    @JsonProperty("published_at")
    private Instant publishedAt;

    private String deadline;

    @JsonProperty("public_link")
    private String publicLink;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
