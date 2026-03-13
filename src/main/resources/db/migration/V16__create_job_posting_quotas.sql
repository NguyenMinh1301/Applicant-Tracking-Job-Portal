-- ============================================================
-- VietRecruit | Migration V16__create_job_posting_quotas.sql
-- Description: Tracks job posting usage per subscription cycle
-- Depends on:  employer_subscriptions
-- ============================================================

CREATE TABLE job_posting_quotas (
    -- Primary key
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign keys
    subscription_id UUID NOT NULL,

    -- Business columns
    jobs_posted     INTEGER     NOT NULL DEFAULT 0,
    jobs_active     INTEGER     NOT NULL DEFAULT 0,
    cycle_start     TIMESTAMPTZ NOT NULL,
    cycle_end       TIMESTAMPTZ NOT NULL,

    -- Audit columns
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Named constraints
    CONSTRAINT fk_quotas_subscription FOREIGN KEY (subscription_id) REFERENCES employer_subscriptions(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_quota_subscription ON job_posting_quotas(subscription_id);

COMMENT ON TABLE  job_posting_quotas             IS 'Enforces per-cycle job posting limits from subscription plan';
COMMENT ON COLUMN job_posting_quotas.jobs_active IS 'Currently PUBLISHED jobs count (decremented on close)';
