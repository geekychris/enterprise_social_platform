-- Conversations table: supports DIRECT (1:1) and GROUP (multi-user) conversations
CREATE TABLE conversations (
    id           BIGINT PRIMARY KEY,
    name         VARCHAR(128),
    type         VARCHAR(10) NOT NULL DEFAULT 'DIRECT',
    created_by   BIGINT REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE conversation_participants (
    conversation_id  BIGINT NOT NULL REFERENCES conversations(id),
    user_id          BIGINT NOT NULL REFERENCES users(id),
    joined_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_read_at     TIMESTAMPTZ,
    visible_from     TIMESTAMPTZ,
    PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX idx_conv_participants_user ON conversation_participants (user_id);

-- Add conversation_id to messages (nullable during migration)
ALTER TABLE messages ADD COLUMN conversation_id BIGINT REFERENCES conversations(id);

-- Migrate existing DMs: create a conversation for each unique sender/recipient pair
-- Use a sequence with the CONVERSATION type byte prefix (0x0C << 56 = 864691128455135232)
CREATE SEQUENCE conv_migration_seq;

-- Create temp mapping table for old pair -> new conversation ID
CREATE TEMP TABLE conv_pair_map AS
SELECT
    LEAST(sender_id, recipient_id) AS user_lo,
    GREATEST(sender_id, recipient_id) AS user_hi,
    864691128455135232 + nextval('conv_migration_seq') AS conv_id,
    MIN(created_at) AS first_message_at
FROM messages
GROUP BY LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id);

-- Insert conversations from the mapping
INSERT INTO conversations (id, type, created_at)
SELECT conv_id, 'DIRECT', first_message_at
FROM conv_pair_map;

-- Insert participants (both sides of each conversation)
INSERT INTO conversation_participants (conversation_id, user_id, joined_at, last_read_at)
SELECT conv_id, user_lo, first_message_at, now()
FROM conv_pair_map;

INSERT INTO conversation_participants (conversation_id, user_id, joined_at, last_read_at)
SELECT conv_id, user_hi, first_message_at, now()
FROM conv_pair_map;

-- Set conversation_id on all existing messages
UPDATE messages m
SET conversation_id = cpm.conv_id
FROM conv_pair_map cpm
WHERE LEAST(m.sender_id, m.recipient_id) = cpm.user_lo
  AND GREATEST(m.sender_id, m.recipient_id) = cpm.user_hi;

-- Make conversation_id NOT NULL
ALTER TABLE messages ALTER COLUMN conversation_id SET NOT NULL;

-- Drop recipient_id (now tracked via conversation_participants)
ALTER TABLE messages DROP COLUMN recipient_id;

-- New indexes
CREATE INDEX idx_messages_conversation_id ON messages (conversation_id, created_at DESC);

-- Drop old indexes that relied on recipient_id
DROP INDEX IF EXISTS idx_messages_conversation;
DROP INDEX IF EXISTS idx_messages_recipient;

-- Cleanup
DROP SEQUENCE conv_migration_seq;
