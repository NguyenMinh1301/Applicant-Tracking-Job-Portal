-- ============================================================
-- VietRecruit | Migration V13__create_categories.sql
-- Description: Job categories / functional areas
-- Depends on:  companies, users
-- ============================================================

CREATE TABLE categories (
    -- Primary key
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign keys
    company_id UUID NOT NULL,
    created_by UUID,
    updated_by UUID,

    -- Business columns
    name       VARCHAR(255) NOT NULL,

    -- Audit columns
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Named constraints
    CONSTRAINT fk_categories_company    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_categories_created_by FOREIGN KEY (created_by) REFERENCES users(id)     ON DELETE SET NULL,
    CONSTRAINT fk_categories_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)     ON DELETE SET NULL
);

CREATE INDEX idx_categories_company_id ON categories(company_id);

COMMENT ON TABLE categories IS 'Job functional categories (e.g. Backend, Frontend, DevOps)';
