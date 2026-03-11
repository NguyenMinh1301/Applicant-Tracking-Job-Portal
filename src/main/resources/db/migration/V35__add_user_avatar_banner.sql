-- Widen avatar_url to accommodate longer external URLs
ALTER TABLE users ALTER COLUMN avatar_url TYPE VARCHAR(500);

-- Add banner and object key columns for R2 storage
ALTER TABLE users ADD COLUMN banner_url VARCHAR(500);
ALTER TABLE users ADD COLUMN avatar_object_key VARCHAR(500);
ALTER TABLE users ADD COLUMN banner_object_key VARCHAR(500);
