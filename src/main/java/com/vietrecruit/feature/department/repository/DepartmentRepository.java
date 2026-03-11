package com.vietrecruit.feature.department.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.vietrecruit.feature.department.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    Page<Department> findByCompanyIdAndDeletedAtIsNull(UUID companyId, Pageable pageable);

    Optional<Department> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);

    boolean existsByCompanyIdAndNameAndDeletedAtIsNull(UUID companyId, String name);

    boolean existsByCompanyIdAndNameAndIdNotAndDeletedAtIsNull(
            UUID companyId, String name, UUID excludeId);
}
