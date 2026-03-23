-- Add status to memberships for approval workflow
ALTER TABLE memberships ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
CREATE INDEX idx_memberships_status ON memberships (status);

-- Add page_memberships for page follow/join with approval
CREATE TABLE page_memberships (
    user_id     BIGINT NOT NULL REFERENCES users(id),
    page_id     BIGINT NOT NULL,
    role        VARCHAR(20) NOT NULL DEFAULT 'FOLLOWER',
    status      VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, page_id)
);
CREATE INDEX idx_page_memberships_page_id ON page_memberships (page_id);
CREATE INDEX idx_page_memberships_status ON page_memberships (status);

-- Messages table for direct messaging
CREATE TABLE messages (
    id              BIGINT PRIMARY KEY,
    sender_id       BIGINT NOT NULL REFERENCES users(id),
    recipient_id    BIGINT NOT NULL REFERENCES users(id),
    content         TEXT,
    read            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_sender ON messages (sender_id, created_at DESC);
CREATE INDEX idx_messages_recipient ON messages (recipient_id, created_at DESC);
CREATE INDEX idx_messages_conversation ON messages (
    LEAST(sender_id, recipient_id),
    GREATEST(sender_id, recipient_id),
    created_at DESC
);

-- Message attachments junction
CREATE TABLE message_attachments (
    message_id      BIGINT NOT NULL REFERENCES messages(id),
    attachment_id   BIGINT NOT NULL REFERENCES attachments(id),
    PRIMARY KEY (message_id, attachment_id)
);
