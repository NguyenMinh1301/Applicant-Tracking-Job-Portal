package com.vietrecruit.feature.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.vietrecruit.feature.application.dto.response.ApplicationScreeningResponse;
import com.vietrecruit.feature.application.entity.Application;
import com.vietrecruit.feature.user.entity.User;

@Mapper(componentModel = "spring")
public interface ScreeningMapper {

    @Mapping(source = "application.id", target = "applicationId")
    @Mapping(source = "application.candidateId", target = "candidateId")
    @Mapping(source = "user.fullName", target = "candidateName")
    @Mapping(source = "user.email", target = "candidateEmail")
    @Mapping(source = "application.aiScore", target = "aiScore")
    @Mapping(target = "applicationStatus", expression = "java(application.getStatus().name())")
    @Mapping(target = "similarityScore", ignore = true)
    @Mapping(target = "scoreBreakdown", ignore = true)
    @Mapping(target = "strengths", ignore = true)
    @Mapping(target = "gaps", ignore = true)
    @Mapping(target = "summary", ignore = true)
    ApplicationScreeningResponse toScreeningResponse(Application application, User user);
}
