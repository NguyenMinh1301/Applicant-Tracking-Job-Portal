package com.vietrecruit.feature.job.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vietrecruit.common.exception.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;
import com.vietrecruit.feature.job.entity.Job;
import com.vietrecruit.feature.job.entity.JobStatus;
import com.vietrecruit.feature.job.repository.JobRepository;
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
    private final QuotaGuard quotaGuard;

    @Override
    @Transactional
    public Job createJob(UUID companyId, String title, String description) {
        var job =
                Job.builder()
                        .companyId(companyId)
                        .title(title)
                        .description(description)
                        .status(JobStatus.DRAFT)
                        .build();
        return jobRepository.save(job);
    }

    @Override
    @Transactional
    public Job publishJob(UUID companyId, UUID jobId) {
        var job = findJobByIdAndCompany(companyId, jobId);

        if (job.getStatus() != JobStatus.DRAFT) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "Only DRAFT jobs can be published");
        }

        // Validate subscription and quota before publishing
        quotaGuard.validateCanPublishJob(companyId);

        job.setStatus(JobStatus.PUBLISHED);
        var saved = jobRepository.save(job);

        // Increment active job count after successful save
        quotaGuard.incrementActiveJobs(companyId);

        log.info("Published job id={} company={}", jobId, companyId);
        return saved;
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

    private Job findJobByIdAndCompany(UUID companyId, UUID jobId) {
        return jobRepository
                .findById(jobId)
                .filter(j -> j.getCompanyId().equals(companyId))
                .orElseThrow(
                        () ->
                                new ApiException(
                                        ApiErrorCode.NOT_FOUND,
                                        "Job not found or does not belong to this company"));
    }
}
