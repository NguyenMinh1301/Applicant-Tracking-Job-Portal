-- Transaction records table (append-only audit log from PayOS webhook data)
CREATE TABLE transaction_records (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_code                 BIGINT NOT NULL REFERENCES payment_transactions(order_code),
    company_id                 UUID NOT NULL REFERENCES companies(id),
    account_number             VARCHAR(50),
    amount                     BIGINT NOT NULL,
    description                TEXT,
    reference                  VARCHAR(100),
    transaction_date_time      TIMESTAMPTZ NOT NULL,
    counter_account_bank_id    VARCHAR(50),
    counter_account_name       VARCHAR(255),
    counter_account_number     VARCHAR(50),
    currency                   VARCHAR(3) NOT NULL DEFAULT 'VND',
    payment_link_id            VARCHAR(100),
    payos_code                 VARCHAR(10) NOT NULL,
    payos_desc                 VARCHAR(255),
    created_at                 TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_tx_records_order_code ON transaction_records(order_code);
CREATE INDEX idx_tx_records_company ON transaction_records(company_id);
