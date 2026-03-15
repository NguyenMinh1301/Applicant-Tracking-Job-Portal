package com.vietrecruit.feature.job.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.vietrecruit.feature.job.dto.request.JobCreateRequest;
import com.vietrecruit.feature.job.dto.request.JobUpdateRequest;
import com.vietrecruit.feature.job.entity.Job;

public interface JobService {

    /** Creates a job in DRAFT status. No quota check at creation. */
    Job createJob(UUID companyId, UUID createdBy, JobCreateRequest request);

    /** Updates a DRAFT job's fields. Only DRAFT jobs can be edited. */
    Job updateJob(UUID companyId, UUID jobId, JobUpdateRequest request);

    /**
     * Publishes a job (DRAFT -> PUBLISHED). Validates subscription + quota via QuotaGuard.
     * Increments active job count.
     */
    Job publishJob(UUID companyId, UUID jobId);

    /** Closes a job (PUBLISHED -> CLOSED). Decrements active job count. */
    Job closeJob(UUID companyId, UUID jobId);

    /** Gets a single job owned by the company. */
    Job getJob(UUID companyId, UUID jobId);

    /** Lists all non-deleted jobs for the company. */
    Page<Job> listJobs(UUID companyId, Pageable pageable);

    /** Lists published, non-deleted jobs for public view with optional filters. */
    Page<Job> listPublicJobs(Pageable pageable, UUID categoryId, UUID locationId, String keyword);

    /** Gets a single published, non-deleted job for public view. */
    Job getPublicJob(UUID jobId);

    Optional<Job> findJobById(UUID jobId);

    Page<Job> searchPublishedJobs(
            String keyword,
            UUID locationId,
            UUID categoryId,
            BigDecimal minSalary,
            Pageable pageable);

    List<Job> findPublishedJobsWithSalary(String titleKeyword);

    List<Job> findAllActiveByIds(List<UUID> ids);

    com.vietrecruit.feature.job.repository.SalaryBenchmarkProjection getSalaryBenchmark(
            UUID categoryId, UUID locationId);
}
