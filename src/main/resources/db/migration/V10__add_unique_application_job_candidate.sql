-- Prevent duplicate applications for same job+candidate (soft-delete aware)
CREATE UNIQUE INDEX uq_application_job_candidate
    ON applications(job_id, candidate_id)
    WHERE deleted_at IS NULL;
