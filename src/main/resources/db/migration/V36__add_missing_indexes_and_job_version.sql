-- HIGH-10: Version column for Job optimistic locking
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- MEDIUM-19: Missing FK index on jobs.location_id
CREATE INDEX IF NOT EXISTS idx_jobs_location_id ON jobs(location_id);

-- MEDIUM-20: Missing FK index on jobs.category_id
CREATE INDEX IF NOT EXISTS idx_jobs_category_id ON jobs(category_id);

-- MEDIUM-21: Missing index on interview_interviewers.user_id (leading column)
CREATE INDEX IF NOT EXISTS idx_ii_user_id ON interview_interviewers(user_id);

-- MEDIUM-22: Composite index for invitation duplicate check
CREATE INDEX IF NOT EXISTS idx_inv_company_email_status ON invitations(company_id, email, status);

-- MEDIUM-23: Partial index for soft-delete filter on applications
CREATE INDEX IF NOT EXISTS idx_applications_not_deleted ON applications(id) WHERE deleted_at IS NULL;
