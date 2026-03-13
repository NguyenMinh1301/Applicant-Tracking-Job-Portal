-- ============================================================
-- VietRecruit | Migration V15__create_employer_subscriptions.sql
-- Description: Active subscription instances per company
-- Depends on:  companies, subscription_plans
-- ============================================================

CREATE TABLE employer_subscriptions (
    -- Primary key
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign keys
    company_id    UUID NOT NULL,
    plan_id       UUID NOT NULL,

    -- Business columns
    status        subscription_status NOT NULL DEFAULT 'ACTIVE',
    started_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at    TIMESTAMPTZ NOT NULL,
    cancelled_at  TIMESTAMPTZ,
    auto_renew    BOOLEAN DEFAULT TRUE,
    payment_ref   VARCHAR(255),
    billing_cycle billing_cycle DEFAULT 'MONTHLY',

    -- Audit columns
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Named constraints
    CONSTRAINT fk_employer_sub_company FOREIGN KEY (company_id) REFERENCES companies(id)          ON DELETE CASCADE,
    CONSTRAINT fk_employer_sub_plan    FOREIGN KEY (plan_id)    REFERENCES subscription_plans(id)  ON DELETE RESTRICT
);

-- Indexes
CREATE INDEX  idx_employer_sub_company ON employer_subscriptions(company_id);
CREATE INDEX  idx_employer_sub_status  ON employer_subscriptions(status)     WHERE status = 'ACTIVE';
CREATE INDEX  idx_employer_sub_expires ON employer_subscriptions(expires_at) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_employer_sub_active ON employer_subscriptions(company_id) WHERE status = 'ACTIVE';

COMMENT ON TABLE  employer_subscriptions          IS 'One active subscription per company at a time';
COMMENT ON COLUMN employer_subscriptions.status   IS 'ACTIVE → EXPIRED (auto) or CANCELLED (manual)';
