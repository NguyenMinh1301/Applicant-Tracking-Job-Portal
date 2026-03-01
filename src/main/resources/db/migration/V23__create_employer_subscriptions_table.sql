CREATE TABLE employer_subscriptions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   UUID NOT NULL REFERENCES companies(id),
    plan_id      UUID NOT NULL REFERENCES subscription_plans(id),
    status       subscription_status NOT NULL DEFAULT 'ACTIVE',
    started_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    auto_renew   BOOLEAN DEFAULT TRUE,
    payment_ref  VARCHAR(255),
    created_at   TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_employer_sub_company ON employer_subscriptions(company_id);
CREATE INDEX idx_employer_sub_status ON employer_subscriptions(status) WHERE status = 'ACTIVE';
CREATE INDEX idx_employer_sub_expires ON employer_subscriptions(expires_at) WHERE status = 'ACTIVE';

-- One active subscription per company at any time
CREATE UNIQUE INDEX uq_employer_sub_active ON employer_subscriptions(company_id) WHERE status = 'ACTIVE';
