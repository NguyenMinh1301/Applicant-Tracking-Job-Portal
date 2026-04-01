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

    /**
     * Creates a job in DRAFT status without consuming quota.
     *
     * @param companyId the owning company's UUID
     * @param createdBy UUID of the user creating the job
     * @param request job details including title, description, and requirements
     * @return the created job entity
     */
    Job createJob(UUID companyId, UUID createdBy, JobCreateRequest request);

    /**
     * Updates a DRAFT job's fields; only DRAFT jobs may be edited.
     *
     * @param companyId the owning company's UUID
     * @param jobId the target job's UUID
     * @param request updated job fields
     * @return the updated job entity
     */
    Job updateJob(UUID companyId, UUID jobId, JobUpdateRequest request);

    /**
     * Publishes a job (DRAFT to PUBLISHED), validating subscription quota via QuotaGuard and
     * incrementing the active job count.
     *
     * @param companyId the owning company's UUID
     * @param jobId the target job's UUID
     * @return the published job entity
     */
    Job publishJob(UUID companyId, UUID jobId);

    /**
     * Closes a job (PUBLISHED to CLOSED) and decrements the active job count.
     *
     * @param companyId the owning company's UUID
     * @param jobId the target job's UUID
     * @return the closed job entity
     */
    Job closeJob(UUID companyId, UUID jobId);

    /**
     * Returns a single non-deleted job owned by the company.
     *
     * @param companyId the owning company's UUID
     * @param jobId the target job's UUID
     * @return the job entity
     */
    Job getJob(UUID companyId, UUID jobId);

    /**
     * Returns a paginated list of all non-deleted jobs for the company.
     *
     * @param companyId the owning company's UUID
     * @param pageable pagination and sort parameters
     * @return page of job entities
     */
    Page<Job> listJobs(UUID companyId, Pageable pageable);

    /**
     * Returns published, non-deleted jobs for public browsing with optional category, location, and
     * keyword filters.
     *
     * @param pageable pagination and sort parameters
     * @param categoryId optional category filter; null to skip
     * @param locationId optional location filter; null to skip
     * @param keyword optional title keyword filter; null to skip
     * @return page of job entities
     */
    Page<Job> listPublicJobs(Pageable pageable, UUID categoryId, UUID locationId, String keyword);

    /**
     * Returns a single published, non-deleted job for public view.
     *
     * @param jobId the target job's UUID
     * @return the job entity
     */
    Job getPublicJob(UUID jobId);

    /**
     * Finds any non-deleted job by ID regardless of status, returning empty if absent.
     *
     * @param jobId the target job's UUID
     * @return Optional containing the job entity, or empty
     */
    Optional<Job> findJobById(UUID jobId);

    /**
     * Searches published jobs by keyword, location, category, and minimum salary using JPA
     * Specifications.
     *
     * @param keyword optional title/description keyword
     * @param locationId optional location filter
     * @param categoryId optional category filter
     * @param minSalary optional minimum salary filter
     * @param pageable pagination and sort parameters
     * @return page of matching job entities
     */
    Page<Job> searchPublishedJobs(
            String keyword,
            UUID locationId,
            UUID categoryId,
            BigDecimal minSalary,
            Pageable pageable);

    /**
     * Returns published jobs with salary data whose title matches the given keyword, used for
     * salary benchmarking.
     *
     * @param titleKeyword partial job title to match
     * @return list of matching job entities
     */
    List<Job> findPublishedJobsWithSalary(String titleKeyword);

    /**
     * Returns all active (non-deleted) jobs whose IDs are in the provided list.
     *
     * @param ids list of job UUIDs to look up
     * @return list of matching active job entities
     */
    List<Job> findAllActiveByIds(List<UUID> ids);

    /**
     * Overwrites the description field of any non-deleted job owned by the company, used by the AI
     * JD generator.
     *
     * @param companyId the owning company's UUID
     * @param jobId the target job's UUID
     * @param description the new description content
     */
    void updateDescription(UUID companyId, UUID jobId, String description);

    /**
     * Returns aggregated salary statistics for published jobs matching the given category and
     * location.
     *
     * @param categoryId the job category UUID
     * @param locationId the location UUID
     * @return salary benchmark projection with min, max, and average values
     */
    com.vietrecruit.feature.job.repository.SalaryBenchmarkProjection getSalaryBenchmark(
            UUID categoryId, UUID locationId);

    /**
     * Returns aggregated salary statistics for published jobs matching the given free-text title
     * and location name.
     *
     * @param jobTitle partial job title keyword
     * @param locationName partial location name keyword
     * @return salary benchmark projection with min, max, and average values
     */
    com.vietrecruit.feature.job.repository.SalaryBenchmarkProjection getSalaryBenchmarkByText(
            String jobTitle, String locationName);
}
