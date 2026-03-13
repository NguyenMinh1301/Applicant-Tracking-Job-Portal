-- ============================================================
-- VietRecruit | Migration V17__create_payment_transactions.sql
-- Description: PayOS payment lifecycle tracking
-- Depends on:  companies, subscription_plans
-- ============================================================

CREATE TABLE payment_transactions (
    -- Primary key
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Business columns
    order_code      BIGINT       UNIQUE NOT NULL,
    company_id      UUID         NOT NULL,
    plan_id         UUID         NOT NULL,
    billing_cycle   billing_cycle NOT NULL DEFAULT 'MONTHLY',
    amount          BIGINT       NOT NULL,
    status          payment_status NOT NULL DEFAULT 'PENDING',
    checkout_url    TEXT,
    payos_reference VARCHAR(255),
    failure_code    VARCHAR(10),
    failure_reason  VARCHAR(255),
    paid_at         TIMESTAMPTZ,
    version         BIGINT       DEFAULT 0,

    -- Audit columns
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Named constraints
    CONSTRAINT fk_payment_tx_company FOREIGN KEY (company_id) REFERENCES companies(id)          ON DELETE RESTRICT,
    CONSTRAINT fk_payment_tx_plan    FOREIGN KEY (plan_id)    REFERENCES subscription_plans(id)  ON DELETE RESTRICT
);

-- Indexes
CREATE INDEX  idx_payment_tx_company        ON payment_transactions(company_id);
CREATE INDEX  idx_payment_tx_status         ON payment_transactions(status) WHERE status = 'PENDING';
CREATE INDEX  idx_payment_tx_order_code     ON payment_transactions(order_code);
CREATE UNIQUE INDEX uq_payment_tx_company_pending ON payment_transactions(company_id) WHERE status = 'PENDING';

COMMENT ON TABLE  payment_transactions              IS 'PayOS payment records — one per checkout session';
COMMENT ON COLUMN payment_transactions.order_code   IS 'Unique numeric code from payos_order_code_seq';
COMMENT ON COLUMN payment_transactions.version      IS 'Optimistic locking for concurrent webhook handling';
COMMENT ON COLUMN payment_transactions.failure_code IS 'Non-00 webhook response code';
