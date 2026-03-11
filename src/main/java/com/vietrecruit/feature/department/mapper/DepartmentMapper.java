package com.vietrecruit.feature.department.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.vietrecruit.feature.department.dto.request.DepartmentRequest;
import com.vietrecruit.feature.department.dto.response.DepartmentResponse;
import com.vietrecruit.feature.department.entity.Department;

@Mapper(componentModel = "spring")
public interface DepartmentMapper {

    DepartmentResponse toResponse(Department department);

    Department toEntity(DepartmentRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(DepartmentRequest request, @MappingTarget Department department);
}
