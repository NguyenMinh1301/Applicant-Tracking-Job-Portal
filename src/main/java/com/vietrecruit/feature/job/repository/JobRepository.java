package com.vietrecruit.feature.job.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.entity.JobStatus;

public interface JobRepository extends JpaRepository<Job, UUID> {

    long countByCompanyIdAndStatus(UUID companyId, JobStatus status);
}
