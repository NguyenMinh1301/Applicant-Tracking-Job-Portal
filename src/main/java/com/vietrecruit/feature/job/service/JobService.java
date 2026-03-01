package com.vietrecruit.feature.job.service;

import java.util.UUID;

import com.vietrecruit.feature.job.entity.Job;

public interface JobService {

    /** Creates a job in DRAFT status. No quota check at creation. */
    Job createJob(UUID companyId, String title, String description);

    /**
     * Publishes a job (DRAFT -> PUBLISHED). Validates subscription + quota via QuotaGuard.
     * Increments active job count.
     */
    Job publishJob(UUID companyId, UUID jobId);

    /** Closes a job (PUBLISHED -> CLOSED). Decrements active job count. */
    Job closeJob(UUID companyId, UUID jobId);
}
