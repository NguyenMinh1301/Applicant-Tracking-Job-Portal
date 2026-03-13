-- ============================================================
-- VietRecruit | Migration V18__create_transaction_records.sql
-- Description: Append-only audit log from PayOS webhook data
-- Depends on:  payment_transactions, companies
-- ============================================================

CREATE TABLE transaction_records (
    -- Primary key
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign keys
    order_code              BIGINT       NOT NULL,
    company_id              UUID         NOT NULL,

    -- Business columns
    account_number          VARCHAR(50),
    amount                  BIGINT       NOT NULL,
    description             TEXT,
    reference               VARCHAR(100),
    transaction_date_time   TIMESTAMPTZ  NOT NULL,
    counter_account_bank_id VARCHAR(50),
    counter_account_name    VARCHAR(255),
    counter_account_number  VARCHAR(50),
    currency                VARCHAR(3)   NOT NULL DEFAULT 'VND',
    payment_link_id         VARCHAR(100),
    payos_code              VARCHAR(10)  NOT NULL,
    payos_desc              VARCHAR(255),

    -- Audit columns
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Named constraints
    CONSTRAINT fk_tx_records_order   FOREIGN KEY (order_code) REFERENCES payment_transactions(order_code) ON DELETE RESTRICT,
    CONSTRAINT fk_tx_records_company FOREIGN KEY (company_id) REFERENCES companies(id)                    ON DELETE RESTRICT
);

-- Indexes
CREATE UNIQUE INDEX uq_tx_records_order_code ON transaction_records(order_code);
CREATE INDEX        idx_tx_records_company   ON transaction_records(company_id);

COMMENT ON TABLE transaction_records IS 'Immutable audit trail of PayOS webhook payment confirmations';
