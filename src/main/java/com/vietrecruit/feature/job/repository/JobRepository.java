package com.vietrecruit.feature.job.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.enums.JobStatus;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    long countByCompanyIdAndStatus(UUID companyId, JobStatus status);

    Page<Job> findByCompanyIdAndDeletedAtIsNull(UUID companyId, Pageable pageable);

    Optional<Job> findByIdAndStatusAndDeletedAtIsNull(UUID id, JobStatus status);

    List<Job> findAllByIdInAndDeletedAtIsNull(List<UUID> ids);
}
