ALTER TABLE users ADD COLUMN IF NOT EXISTS is_admin BOOLEAN NOT NULL DEFAULT FALSE;
-- Make the first few users admins for testing
UPDATE users SET is_admin = TRUE WHERE id IN (
    SELECT id FROM users ORDER BY id LIMIT 3
);
