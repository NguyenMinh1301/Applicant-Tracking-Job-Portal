-- ============================================================
-- VietRecruit | Migration V5__create_subscription_plans.sql
-- Description: Subscription plan catalog for employers
-- Depends on:  none
-- ============================================================

CREATE TABLE subscription_plans (
    -- Primary key
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Business columns
    code              VARCHAR(50)    UNIQUE NOT NULL,
    name              VARCHAR(100)   NOT NULL,
    description       TEXT,
    max_active_jobs   INTEGER        NOT NULL DEFAULT 1,
    job_duration_days INTEGER        NOT NULL DEFAULT 30,
    resume_access     BOOLEAN        DEFAULT FALSE,
    ai_matching       BOOLEAN        DEFAULT FALSE,
    priority_listing  BOOLEAN        DEFAULT FALSE,
    price_monthly     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    price_yearly      NUMERIC(12, 2),
    currency          VARCHAR(10)    DEFAULT 'VND',
    is_active         BOOLEAN        DEFAULT TRUE,

    -- Audit columns
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  subscription_plans                   IS 'Tiered pricing plans for employer subscriptions';
COMMENT ON COLUMN subscription_plans.max_active_jobs   IS 'Maximum concurrent published jobs. -1 = unlimited';
COMMENT ON COLUMN subscription_plans.job_duration_days IS 'Days a job posting stays active before auto-close';
