CREATE TABLE job_posting_quotas (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES employer_subscriptions(id),
    jobs_posted     INTEGER NOT NULL DEFAULT 0,
    jobs_active     INTEGER NOT NULL DEFAULT 0,
    cycle_start     TIMESTAMPTZ NOT NULL,
    cycle_end       TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_quota_subscription ON job_posting_quotas(subscription_id);
