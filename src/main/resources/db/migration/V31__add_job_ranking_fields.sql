-- ============================================================
-- VietRecruit | Migration V31__add_job_ranking_fields.sql
-- Description: Add ranking/scoring fields for ES function_score
-- Depends on:  jobs (V19)
-- ============================================================

ALTER TABLE jobs
    ADD COLUMN view_count        INTEGER   NOT NULL DEFAULT 0,
    ADD COLUMN application_count INTEGER   NOT NULL DEFAULT 0,
    ADD COLUMN is_hot            BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN is_featured       BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN published_at      TIMESTAMPTZ;

CREATE INDEX idx_jobs_published_at ON jobs(published_at);
