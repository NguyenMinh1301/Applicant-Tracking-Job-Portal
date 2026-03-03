-- Add FAILED to payment_status enum
ALTER TYPE payment_status ADD VALUE IF NOT EXISTS 'FAILED';

-- Add audit columns for non-00 webhook responses
ALTER TABLE payment_transactions
    ADD COLUMN failure_code   VARCHAR(10),
    ADD COLUMN failure_reason  VARCHAR(255);

-- Prevent concurrent pending payments per company
CREATE UNIQUE INDEX uq_payment_tx_company_pending
    ON payment_transactions(company_id) WHERE status = 'PENDING';
