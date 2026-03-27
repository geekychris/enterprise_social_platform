-- Workflow automation triggers
CREATE TABLE bot_triggers (
    id               BIGINT PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id),
    event_type       VARCHAR(30) NOT NULL,
    condition_pattern TEXT,
    action_type      VARCHAR(30) NOT NULL,
    action_params    TEXT,
    cron_expression  VARCHAR(50),
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_bot_triggers_user ON bot_triggers (user_id);
CREATE INDEX idx_bot_triggers_event ON bot_triggers (event_type, active);

-- Performance index for reaction analytics
CREATE INDEX IF NOT EXISTS idx_reactions_target_created ON reactions (target_id, created_at);
