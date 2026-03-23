ALTER TABLE attachments ADD COLUMN content_hash VARCHAR(64);
CREATE UNIQUE INDEX idx_attachments_content_hash ON attachments (content_hash) WHERE content_hash IS NOT NULL;
