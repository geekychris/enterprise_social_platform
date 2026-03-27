-- Polls system
CREATE TABLE polls (
    id              BIGINT PRIMARY KEY,
    post_id         BIGINT NOT NULL,
    question        VARCHAR(500) NOT NULL,
    allow_multiple  BOOLEAN NOT NULL DEFAULT FALSE,
    closes_at       TIMESTAMPTZ,
    created_by      BIGINT NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_polls_post_id ON polls (post_id);

CREATE TABLE poll_options (
    id              BIGINT PRIMARY KEY,
    poll_id         BIGINT NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    label           VARCHAR(200) NOT NULL,
    sort_order      SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_poll_options_poll_id ON poll_options (poll_id);

CREATE TABLE poll_votes (
    poll_id         BIGINT NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
    option_id       BIGINT NOT NULL REFERENCES poll_options(id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    voted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (poll_id, user_id, option_id)
);
CREATE INDEX idx_poll_votes_option ON poll_votes (option_id);

-- Bot cross-conversation memory
CREATE TABLE bot_memory (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    memory_key      VARCHAR(128) NOT NULL,
    memory_value    TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, memory_key)
);
CREATE INDEX idx_bot_memory_user ON bot_memory (user_id);

-- Daily digest tracking
CREATE TABLE daily_digest_log (
    user_id         BIGINT NOT NULL REFERENCES users(id),
    digest_date     DATE NOT NULL,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, digest_date)
);

-- User preference for digests
ALTER TABLE users ADD COLUMN digest_enabled BOOLEAN NOT NULL DEFAULT TRUE;
