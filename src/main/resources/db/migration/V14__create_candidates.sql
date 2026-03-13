-- ============================================================
-- VietRecruit | Migration V14__create_candidates.sql
-- Description: Candidate profiles with CV metadata and job
--              matching preferences
-- Depends on:  users
-- ============================================================

CREATE TABLE candidates (
    -- Primary key
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Foreign keys
    user_id                UUID NOT NULL,

    -- CV columns
    headline               VARCHAR(255),
    summary                TEXT,
    default_cv_url         VARCHAR(255),
    cv_embedding           vector(1536),
    parsed_cv_text         TEXT,
    cv_file_size_bytes     BIGINT,
    cv_original_filename   VARCHAR(255),
    cv_content_type        VARCHAR(100),
    cv_uploaded_at         TIMESTAMPTZ,

    -- Profile / job-matching columns
    desired_position       VARCHAR(100),
    desired_position_level VARCHAR(50),
    years_of_experience    SMALLINT,
    skills                 TEXT[],
    primary_language       VARCHAR(50),
    work_type              VARCHAR(20),
    desired_salary_min     BIGINT,
    desired_salary_max     BIGINT,
    available_from         DATE,
    education_level        VARCHAR(50),
    education_major        VARCHAR(100),
    is_open_to_work        BOOLEAN DEFAULT TRUE,

    -- Audit columns
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at             TIMESTAMPTZ,

    -- Named constraints
    CONSTRAINT fk_candidates_user     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_candidates_user_id  UNIQUE (user_id)
);

-- Indexes
CREATE INDEX idx_candidates_user_id ON candidates(user_id);
CREATE INDEX hnsw_candidate_cv_idx  ON candidates USING hnsw (cv_embedding vector_cosine_ops);

COMMENT ON TABLE  candidates                IS 'Extended profile for users with CANDIDATE role';
COMMENT ON COLUMN candidates.cv_embedding   IS '1536-dim OpenAI embedding of parsed CV text for AI matching';
COMMENT ON COLUMN candidates.work_type      IS 'REMOTE | HYBRID | ONSITE';
COMMENT ON COLUMN candidates.is_open_to_work IS 'Visibility flag for recruiter searches';
