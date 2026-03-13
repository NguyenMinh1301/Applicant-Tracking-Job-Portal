-- ============================================================
-- VietRecruit | Migration V19__create_jobs.sql
-- Description: Job postings published by employers
-- Depends on:  companies, departments, locations, categories,
--              users
-- ============================================================

CREATE TABLE jobs (
    -- Primary key
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign keys
    company_id    UUID NOT NULL,
    department_id UUID,
    location_id   UUID,
    category_id   UUID,
    created_by    UUID,
    updated_by    UUID,

    -- Business columns
    title         VARCHAR(255) NOT NULL,
    description   TEXT         NOT NULL,
    requirements  TEXT,
    min_salary    NUMERIC(15, 2),
    max_salary    NUMERIC(15, 2),
    currency      VARCHAR(10)  DEFAULT 'VND',
    is_negotiable BOOLEAN      DEFAULT FALSE,
    status        job_status   DEFAULT 'DRAFT',
    deadline      DATE,
    public_link   VARCHAR(255),
    embedding     vector(1536),

    -- Audit columns
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMPTZ,

    -- Named constraints
    CONSTRAINT fk_jobs_company    FOREIGN KEY (company_id)    REFERENCES companies(id)    ON DELETE CASCADE,
    CONSTRAINT fk_jobs_department FOREIGN KEY (department_id) REFERENCES departments(id)  ON DELETE SET NULL,
    CONSTRAINT fk_jobs_location   FOREIGN KEY (location_id)   REFERENCES locations(id)    ON DELETE SET NULL,
    CONSTRAINT fk_jobs_category   FOREIGN KEY (category_id)   REFERENCES categories(id)   ON DELETE SET NULL,
    CONSTRAINT fk_jobs_created_by FOREIGN KEY (created_by)    REFERENCES users(id)        ON DELETE SET NULL,
    CONSTRAINT fk_jobs_updated_by FOREIGN KEY (updated_by)    REFERENCES users(id)        ON DELETE SET NULL,
    CONSTRAINT uq_jobs_public_link UNIQUE (public_link)
);

-- Indexes
CREATE INDEX idx_jobs_company_id    ON jobs(company_id);
CREATE INDEX idx_jobs_department_id ON jobs(department_id);
CREATE INDEX idx_jobs_status        ON jobs(status);
CREATE INDEX idx_jobs_deleted_at    ON jobs(deleted_at);
CREATE INDEX hnsw_job_embedding_idx ON jobs USING hnsw (embedding vector_cosine_ops);

COMMENT ON TABLE  jobs              IS 'Job postings with AI vector embeddings for semantic search';
COMMENT ON COLUMN jobs.embedding    IS '1536-dim OpenAI embedding of title+description for similarity matching';
COMMENT ON COLUMN jobs.public_link  IS 'Unique slug for public-facing job page URL';
