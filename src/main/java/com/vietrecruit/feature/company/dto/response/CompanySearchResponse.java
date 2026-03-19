package com.vietrecruit.feature.company.dto.response;

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
public class CompanySearchResponse {
    private String id;
    private String name;
    private String domain;
    private String website;
    private Instant createdAt;
    private Map<String, List<String>> highlights;
    private Double score;
}
