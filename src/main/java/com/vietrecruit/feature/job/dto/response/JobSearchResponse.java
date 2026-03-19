package com.vietrecruit.feature.job.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobSearchResponse {
    private String id;
    private String title;
    private String description;
    private String requirements;
    private String companyName;
    private String categoryName;
    private String locationName;
    private Double minSalary;
    private Double maxSalary;
    private String currency;
    private Boolean isNegotiable;
    private String status;
    private String publicLink;
    private Instant createdAt;
    private Map<String, List<String>> highlights;
    private Double score;
}
