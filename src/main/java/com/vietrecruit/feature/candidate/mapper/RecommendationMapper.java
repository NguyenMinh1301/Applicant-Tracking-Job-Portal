package com.vietrecruit.feature.candidate.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.vietrecruit.feature.job.dto.response.JobRecommendationResponse;
import com.vietrecruit.feature.job.entity.Job;

@Mapper(componentModel = "spring")
public interface RecommendationMapper {

    @Mapping(target = "jobId", expression = "java(job.getId().toString())")
    @Mapping(source = "job.title", target = "title")
    @Mapping(target = "companyName", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(source = "matchScore", target = "matchScore")
    @Mapping(source = "matchReason", target = "matchReason")
    JobRecommendationResponse toResponse(Job job, Double matchScore, String matchReason);
}
