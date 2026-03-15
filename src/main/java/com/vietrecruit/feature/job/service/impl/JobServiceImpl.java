package com.vietrecruit.feature.job.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.ai.event.JobPublishedEvent;
import com.vietrecruit.feature.job.dto.request.JobCreateRequest;
import com.vietrecruit.feature.job.dto.request.JobUpdateRequest;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.enums.JobStatus;
import com.vietrecruit.feature.job.mapper.JobMapper;
import com.vietrecruit.feature.job.repository.JobRepository;
import com.vietrecruit.feature.job.repository.JobSpecification;
import com.vietrecruit.feature.job.service.JobService;
import com.vietrecruit.feature.subscription.service.QuotaGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final JobMapper jobMapper;
    private final QuotaGuard quotaGuard;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    @Transactional
    public Job createJob(UUID companyId, UUID createdBy, JobCreateRequest request) {
        var job = jobMapper.toEntity(request);
        job.setCompanyId(companyId);
        job.setCreatedBy(createdBy);
        job.setStatus(JobStatus.DRAFT);
        return jobRepository.save(job);
    }

    @Override
    @Transactional
    public Job updateJob(UUID companyId, UUID jobId, JobUpdateRequest request) {
        var job = findJobByIdAndCompany(companyId, jobId);

        if (job.getStatus() != JobStatus.DRAFT) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "Only DRAFT jobs can be edited");
        }

        jobMapper.updateEntity(request, job);
        return jobRepository.save(job);
    }

    @Override
    @Transactional
    public Job publishJob(UUID companyId, UUID jobId) {
        var job = findJobByIdAndCompany(companyId, jobId);

        if (job.getStatus() != JobStatus.DRAFT) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "Only DRAFT jobs can be published");
        }

        // Retry quota operations on optimistic lock failure (max 3 attempts)
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                quotaGuard.validateCanPublishJob(companyId);
                job.setStatus(JobStatus.PUBLISHED);
                var saved = jobRepository.save(job);
                quotaGuard.incrementActiveJobs(companyId);
                log.info("Published job id={} company={}", jobId, companyId);
                try {
                    JobPublishedEvent event =
                            new JobPublishedEvent(
                                    saved.getId(), saved.getCompanyId(), saved.getTitle());
                    kafkaTemplate
                            .send("ai.job-published", saved.getId().toString(), event)
                            .whenComplete(
                                    (res, ex) -> {
                                        if (ex != null) {
                                            log.warn(
                                                    "Failed to publish job published event: jobId={}",
                                                    saved.getId(),
                                                    ex);
                                        }
                                    });
                } catch (Exception e) {
                    log.warn("Failed to publish job published event: jobId={}", saved.getId(), e);
                }
                return saved;
            } catch (ApiException e) {
                if (ApiErrorCode.CONFLICT.equals(e.getErrorCode()) && attempt < maxAttempts) {
                    log.warn(
                            "Quota optimistic lock retry attempt {}/{} for job={}",
                            attempt,
                            maxAttempts,
                            jobId);
                    continue;
                }
                throw e;
            }
        }
        // Unreachable — loop always returns or throws
        throw new ApiException(
                ApiErrorCode.INTERNAL_ERROR, "Failed to publish job after retry exhaustion");
    }

    @Override
    @Transactional
    public Job closeJob(UUID companyId, UUID jobId) {
        var job = findJobByIdAndCompany(companyId, jobId);

        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "Only PUBLISHED jobs can be closed");
        }

        job.setStatus(JobStatus.CLOSED);
        var saved = jobRepository.save(job);

        // Decrement active job count after successful save
        quotaGuard.decrementActiveJobs(companyId);

        log.info("Closed job id={} company={}", jobId, companyId);
        return saved;
    }

    @Override
    public Job getJob(UUID companyId, UUID jobId) {
        return findJobByIdAndCompany(companyId, jobId);
    }

    @Override
    public Page<Job> listJobs(UUID companyId, Pageable pageable) {
        return jobRepository.findByCompanyIdAndDeletedAtIsNull(companyId, pageable);
    }

    @Override
    public Page<Job> listPublicJobs(
            Pageable pageable, UUID categoryId, UUID locationId, String keyword) {
        Specification<Job> spec = JobSpecification.isPublished();

        if (categoryId != null) {
            spec = spec.and(JobSpecification.hasCategoryId(categoryId));
        }
        if (locationId != null) {
            spec = spec.and(JobSpecification.hasLocationId(locationId));
        }
        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and(JobSpecification.titleContains(keyword));
        }

        return jobRepository.findAll(spec, pageable);
    }

    @Override
    public Job getPublicJob(UUID jobId) {
        return jobRepository
                .findByIdAndStatusAndDeletedAtIsNull(jobId, JobStatus.PUBLISHED)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ApiErrorCode.NOT_FOUND,
                                        "Job not found or is not published"));
    }

    private Job findJobByIdAndCompany(UUID companyId, UUID jobId) {
        return jobRepository
                .findById(jobId)
                .filter(j -> j.getCompanyId().equals(companyId))
                .filter(j -> j.getDeletedAt() == null)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ApiErrorCode.NOT_FOUND,
                                        "Job not found or does not belong to this company"));
    }

    @Override
    public Optional<Job> findJobById(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    @Override
    public Page<Job> searchPublishedJobs(
            String keyword,
            UUID locationId,
            UUID categoryId,
            BigDecimal minSalary,
            Pageable pageable) {
        Specification<Job> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(root.get("status"), JobStatus.PUBLISHED));
                    predicates.add(cb.isNull(root.get("deletedAt")));

                    if (keyword != null && !keyword.isBlank()) {
                        String pattern = "%" + keyword.toLowerCase() + "%";
                        predicates.add(
                                cb.or(
                                        cb.like(cb.lower(root.get("title")), pattern),
                                        cb.like(cb.lower(root.get("description")), pattern)));
                    }
                    if (locationId != null) {
                        predicates.add(cb.equal(root.get("locationId"), locationId));
                    }
                    if (categoryId != null) {
                        predicates.add(cb.equal(root.get("categoryId"), categoryId));
                    }
                    if (minSalary != null) {
                        predicates.add(cb.greaterThanOrEqualTo(root.get("maxSalary"), minSalary));
                    }

                    return cb.and(predicates.toArray(new Predicate[0]));
                };
        return jobRepository.findAll(spec, pageable);
    }

    @Override
    public List<Job> findPublishedJobsWithSalary(String titleKeyword) {
        Specification<Job> spec =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(root.get("status"), JobStatus.PUBLISHED));
                    predicates.add(cb.isNull(root.get("deletedAt")));
                    predicates.add(cb.isNotNull(root.get("minSalary")));
                    predicates.add(cb.isNotNull(root.get("maxSalary")));

                    if (titleKeyword != null && !titleKeyword.isBlank()) {
                        String pattern = "%" + titleKeyword.toLowerCase() + "%";
                        predicates.add(cb.like(cb.lower(root.get("title")), pattern));
                    }

                    return cb.and(predicates.toArray(new Predicate[0]));
                };
        return jobRepository.findAll(spec);
    }

    @Override
    public List<Job> findAllActiveByIds(List<UUID> ids) {
        return jobRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public com.vietrecruit.feature.job.repository.SalaryBenchmarkProjection getSalaryBenchmark(
            UUID categoryId, UUID locationId) {
        return jobRepository.getSalaryBenchmark(categoryId, locationId);
    }
}
