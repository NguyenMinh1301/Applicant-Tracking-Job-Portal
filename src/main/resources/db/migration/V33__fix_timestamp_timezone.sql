-- ============================================================
-- VietRecruit | Migration V33__fix_timestamp_timezone.sql
-- Description: Convert TIMESTAMP columns to TIMESTAMPTZ to
--              ensure correct timezone-aware storage
-- ============================================================

ALTER TABLE applications ALTER COLUMN ai_scored_at TYPE TIMESTAMPTZ USING ai_scored_at AT TIME ZONE 'UTC';
ALTER TABLE invitations ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC';
ALTER TABLE invitations ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE knowledge_documents ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE knowledge_documents ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
