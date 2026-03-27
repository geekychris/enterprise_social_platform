-- Add is_bot flag to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_bot BOOLEAN NOT NULL DEFAULT FALSE;

-- Create the system bot user with a high ID to avoid conflicts
INSERT INTO users (id, username, display_name, email, password_hash, is_bot, visibility, is_admin)
VALUES (
    72057594037999999,
    'roid',
    'Roid',
    'roid@worksphere.local',
    '$2a$10$DISABLED_BOT_ACCOUNT_NO_LOGIN',
    TRUE,
    'PUBLIC',
    FALSE
)
ON CONFLICT (id) DO UPDATE SET is_bot = TRUE, username = 'roid', display_name = 'Roid';
