package com.vietrecruit.feature.company.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.vietrecruit.feature.company.dto.response.CompanyMemberResponse;
import com.vietrecruit.feature.user.entity.User;

@Mapper(componentModel = "spring")
public interface CompanyMemberMapper {

    @Mapping(target = "role", expression = "java(extractPrimaryRole(user))")
    @Mapping(target = "active", source = "isActive")
    CompanyMemberResponse toResponse(User user);

    default String extractPrimaryRole(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return null;
        }
        return user.getRoles().stream().findFirst().map(role -> role.getCode()).orElse(null);
    }
}
