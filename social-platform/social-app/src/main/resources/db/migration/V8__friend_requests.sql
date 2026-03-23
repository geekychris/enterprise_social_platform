CREATE TABLE friend_requests (
    id BIGINT PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    receiver_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (sender_id, receiver_id)
);

CREATE INDEX idx_friend_requests_receiver_status ON friend_requests (receiver_id, status);
CREATE INDEX idx_friend_requests_sender_status ON friend_requests (sender_id, status);
