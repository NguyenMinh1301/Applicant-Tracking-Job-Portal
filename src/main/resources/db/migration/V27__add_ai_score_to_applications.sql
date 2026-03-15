-- ============================================================
-- VietRecruit | Migration V27__add_ai_score_to_applications.sql
-- Description: Add AI screening score columns to applications
--              table for caching agent-computed scores.
-- ============================================================

ALTER TABLE applications
    ADD COLUMN IF NOT EXISTS ai_score INTEGER,
    ADD COLUMN IF NOT EXISTS ai_score_breakdown JSONB,
    ADD COLUMN IF NOT EXISTS ai_scored_at TIMESTAMP;

COMMENT ON COLUMN applications.ai_score           IS 'AI screening score 0-100, -1 means parse failure needing manual review, NULL means not yet scored';
COMMENT ON COLUMN applications.ai_score_breakdown  IS 'JSON breakdown: skillMatch, experienceMatch, educationMatch, strengths, gaps, summary';
COMMENT ON COLUMN applications.ai_scored_at        IS 'Timestamp when AI scoring was last performed';
