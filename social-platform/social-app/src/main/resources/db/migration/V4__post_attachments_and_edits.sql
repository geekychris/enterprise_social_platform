-- Post-attachment junction table
CREATE TABLE post_attachments (
    post_id         BIGINT NOT NULL,
    attachment_id   BIGINT NOT NULL REFERENCES attachments(id),
    sort_order      INT NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id, attachment_id)
);
CREATE INDEX idx_post_attachments_post_id ON post_attachments (post_id);

-- Comment-attachment junction table
CREATE TABLE comment_attachments (
    comment_id      BIGINT NOT NULL,
    attachment_id   BIGINT NOT NULL REFERENCES attachments(id),
    PRIMARY KEY (comment_id, attachment_id)
);

-- Add pinned_post_id to groups and pages
ALTER TABLE groups_ ADD COLUMN pinned_post_id BIGINT;
ALTER TABLE pages ADD COLUMN pinned_post_id BIGINT;
