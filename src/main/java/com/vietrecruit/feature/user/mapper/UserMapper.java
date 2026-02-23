package com.vietrecruit.feature.user.mapper;

import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.vietrecruit.feature.user.dto.request.UpdateProfileRequest;
import com.vietrecruit.feature.user.dto.request.UserRequest;
import com.vietrecruit.feature.user.dto.response.AdminUserResponse;
import com.vietrecruit.feature.user.dto.response.UserProfileResponse;
import com.vietrecruit.feature.user.entity.Role;
import com.vietrecruit.feature.user.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // --- Client mappings ---

    UserProfileResponse toProfileResponse(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "companyId", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "isLocked", ignore = true)
    @Mapping(target = "failedAttempts", ignore = true)
    @Mapping(target = "lockUntil", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "roles", ignore = true)
    void updateProfile(@MappingTarget User user, UpdateProfileRequest request);

    // --- Admin mappings ---

    @Mapping(target = "roles", source = "roles", qualifiedByName = "rolesToCodes")
    AdminUserResponse toAdminResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "companyId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "isLocked", ignore = true)
    @Mapping(target = "failedAttempts", ignore = true)
    @Mapping(target = "lockUntil", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "roles", ignore = true)
    User toEntity(UserRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "companyId", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "isLocked", ignore = true)
    @Mapping(target = "failedAttempts", ignore = true)
    @Mapping(target = "lockUntil", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "roles", ignore = true)
    void updateEntity(@MappingTarget User user, UserRequest request);

    // --- Shared helpers ---

    @Named("rolesToCodes")
    default Set<String> rolesToCodes(Set<Role> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream().map(Role::getCode).collect(Collectors.toSet());
    }
}
