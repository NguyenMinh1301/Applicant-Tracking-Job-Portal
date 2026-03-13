-- ============================================================
-- VietRecruit | Migration V11__create_departments.sql
-- Description: Internal departments within employer companies
-- Depends on:  companies, users
-- ============================================================

CREATE TABLE departments (
    -- Primary key
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign keys
    company_id UUID NOT NULL,
    created_by UUID,
    updated_by UUID,

    -- Business columns
    name        VARCHAR(255) NOT NULL,
    description TEXT,

    -- Audit columns
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    -- Named constraints
    CONSTRAINT fk_departments_company    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_departments_created_by FOREIGN KEY (created_by) REFERENCES users(id)     ON DELETE SET NULL,
    CONSTRAINT fk_departments_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)     ON DELETE SET NULL
);

CREATE INDEX idx_departments_company_id ON departments(company_id);

COMMENT ON TABLE departments IS 'Organizational units within an employer company';
