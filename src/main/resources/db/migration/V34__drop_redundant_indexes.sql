-- ============================================================
-- VietRecruit | Migration V34__drop_redundant_indexes.sql
-- Description: Drop indexes that duplicate implicit B-tree
--              indexes created by UNIQUE constraints
--
-- idx_candidates_user_id is redundant with UNIQUE constraint
--   uq_candidates_user_id (V14__create_candidates.sql)
-- idx_users_email is redundant with UNIQUE constraint on the
--   email column (V6__create_users.sql)
-- ============================================================

DROP INDEX IF EXISTS idx_candidates_user_id;
DROP INDEX IF EXISTS idx_users_email;
