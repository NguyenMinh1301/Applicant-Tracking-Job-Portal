package com.vietrecruit.feature.candidate.document;

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
public class CandidateDocument {

    private String id;

    @JsonProperty("user_id")
    private String userId;

    private String headline;
    private String summary;

    @JsonProperty("desired_position")
    private String desiredPosition;

    @JsonProperty("desired_position_level")
    private String desiredPositionLevel;

    @JsonProperty("years_of_experience")
    private Short yearsOfExperience;

    private String[] skills;

    @JsonProperty("work_type")
    private String workType;

    @JsonProperty("desired_salary_min")
    private Long desiredSalaryMin;

    @JsonProperty("desired_salary_max")
    private Long desiredSalaryMax;

    @JsonProperty("education_level")
    private String educationLevel;

    @JsonProperty("education_major")
    private String educationMajor;

    @JsonProperty("is_open_to_work")
    private Boolean isOpenToWork;

    @JsonProperty("available_from")
    private String availableFrom;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
