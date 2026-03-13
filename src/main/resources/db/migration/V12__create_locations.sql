-- ============================================================
-- VietRecruit | Migration V12__create_locations.sql
-- Description: Physical or remote work locations per company
-- Depends on:  companies, users
-- ============================================================

CREATE TABLE locations (
    -- Primary key
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign keys
    company_id UUID NOT NULL,
    created_by UUID,
    updated_by UUID,

    -- Business columns
    name       VARCHAR(255) NOT NULL,
    address    TEXT,

    -- Audit columns
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Named constraints
    CONSTRAINT fk_locations_company    FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_locations_created_by FOREIGN KEY (created_by) REFERENCES users(id)     ON DELETE SET NULL,
    CONSTRAINT fk_locations_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)     ON DELETE SET NULL
);

CREATE INDEX idx_locations_company_id ON locations(company_id);

COMMENT ON TABLE locations IS 'Office addresses or remote designations for job postings';
