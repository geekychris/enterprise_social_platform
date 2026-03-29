-- ============================================================
-- Phase 1: Denormalized messaging tables for scalability
-- ============================================================

-- Conversation summary: eliminates N+1 query for conversation list
CREATE TABLE conversation_summaries (
    conversation_id  BIGINT PRIMARY KEY REFERENCES conversations(id),
    last_message_id  BIGINT,
    last_message_at  TIMESTAMPTZ,
    last_message_preview TEXT,
    last_sender_id   BIGINT,
    last_sender_name VARCHAR(128),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Materialized unread counts: O(conversations) instead of O(messages)
CREATE TABLE unread_counts (
    user_id          BIGINT NOT NULL,
    conversation_id  BIGINT NOT NULL,
    unread_count     INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, conversation_id)
);
CREATE INDEX idx_unread_counts_user ON unread_counts (user_id);

-- Populate conversation_summaries from existing data
INSERT INTO conversation_summaries (conversation_id, last_message_id, last_message_at, last_message_preview, last_sender_id, updated_at)
SELECT DISTINCT ON (m.conversation_id)
    m.conversation_id,
    m.id,
    m.created_at,
    LEFT(m.content, 100),
    m.sender_id,
    now()
FROM messages m
ORDER BY m.conversation_id, m.created_at DESC;

-- Populate unread_counts from existing data
INSERT INTO unread_counts (user_id, conversation_id, unread_count)
SELECT
    cp.user_id,
    cp.conversation_id,
    COUNT(m.id)
FROM conversation_participants cp
LEFT JOIN messages m ON m.conversation_id = cp.conversation_id
    AND m.sender_id != cp.user_id
    AND (cp.last_read_at IS NULL OR m.created_at > cp.last_read_at)
GROUP BY cp.user_id, cp.conversation_id;

-- ============================================================
-- Phase 3: Feed pre-computation support
-- ============================================================

-- User feed cache: stores pre-computed feed entries
CREATE TABLE feed_entries (
    user_id     BIGINT NOT NULL,
    post_id     BIGINT NOT NULL,
    score       DOUBLE PRECISION NOT NULL DEFAULT 0,
    source      VARCHAR(20),  -- ORGANIC, RECOMMENDED, TRENDING
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, post_id)
);
CREATE INDEX idx_feed_entries_user_score ON feed_entries (user_id, score DESC);

-- Rate limiting tracking
CREATE TABLE rate_limits (
    client_key   VARCHAR(128) NOT NULL,
    endpoint     VARCHAR(64) NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    request_count INT NOT NULL DEFAULT 1,
    PRIMARY KEY (client_key, endpoint, window_start)
);
CREATE INDEX idx_rate_limits_cleanup ON rate_limits (window_start);
