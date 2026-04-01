-- ═══════════════════════════════════════════════════════════════
-- A. Composite indexes for multi-tenant query patterns
-- Each covers WHERE tenant_id = ? AND ... ORDER BY created_at
-- ═══════════════════════════════════════════════════════════════

-- Drop old single-column tenant indexes (replaced by composites)
DROP INDEX IF EXISTS idx_users_tenant;
DROP INDEX IF EXISTS idx_reactions_tenant;
DROP INDEX IF EXISTS idx_messages_tenant;
DROP INDEX IF EXISTS idx_conversations_tenant;
DROP INDEX IF EXISTS idx_follows_tenant;
DROP INDEX IF EXISTS idx_memberships_tenant;
DROP INDEX IF EXISTS idx_groups_tenant;
DROP INDEX IF EXISTS idx_feed_entries_tenant;

-- Users: tenant + username lookup, tenant + email lookup
CREATE INDEX idx_users_tenant_username ON users(tenant_id, username);
CREATE INDEX idx_users_tenant_email ON users(tenant_id, email);
CREATE INDEX idx_users_tenant_created ON users(tenant_id, created_at DESC);

-- Posts: tenant + author, tenant + target (group/page feed), tenant + created (feed assembly)
-- Note: posts is partitioned by created_at, so indexes go on each partition
CREATE INDEX idx_posts_tenant_author ON posts(tenant_id, author_id, created_at DESC);
CREATE INDEX idx_posts_tenant_target ON posts(tenant_id, target_type, target_id, created_at DESC);

-- Comments: tenant + post (comment threads)
CREATE INDEX idx_comments_tenant_post ON comments(tenant_id, post_id, created_at DESC);

-- Reactions: tenant + target (reaction counts), tenant + user + target (current user reaction)
CREATE INDEX idx_reactions_tenant_target ON reactions(tenant_id, target_id, reaction_type);
CREATE INDEX idx_reactions_tenant_user ON reactions(tenant_id, user_id, target_id);

-- Messages: tenant + conversation + time (message thread loading)
CREATE INDEX idx_messages_tenant_conv ON messages(tenant_id, conversation_id, created_at DESC);

-- Conversations: tenant + type (direct/group listing)
CREATE INDEX idx_conversations_tenant_type ON conversations(tenant_id, type, created_at DESC);

-- Conversation participants: tenant + user (conversation list)
CREATE INDEX idx_conv_parts_tenant_user ON conversation_participants(tenant_id, user_id);

-- Follows: tenant + follower (who I follow), tenant + followed (my followers)
CREATE INDEX idx_follows_tenant_follower ON follows(tenant_id, follower_id);
CREATE INDEX idx_follows_tenant_followed ON follows(tenant_id, followed_id);

-- Memberships: tenant + user (my groups), tenant + group (group members)
CREATE INDEX idx_memberships_tenant_user ON memberships(tenant_id, user_id, status);
CREATE INDEX idx_memberships_tenant_group ON memberships(tenant_id, group_id, status);

-- Groups: tenant + name search
CREATE INDEX idx_groups_tenant_name ON groups_(tenant_id, name);

-- Pages: tenant + name search
CREATE INDEX idx_pages_tenant_name ON pages(tenant_id, name);

-- Feed entries: tenant + user + score (pre-computed feed)
CREATE INDEX idx_feed_tenant_user ON feed_entries(tenant_id, user_id, score DESC);

-- Notifications: tenant + user + read status
CREATE INDEX idx_notifications_tenant_user ON notifications(tenant_id, user_id, read, created_at DESC);

-- Unread counts: tenant + user (badge count)
CREATE INDEX idx_unread_tenant_user ON unread_counts(tenant_id, user_id);

-- Conversation summaries: tenant + last timestamp (conversation list sorting)
CREATE INDEX idx_conv_summary_tenant ON conversation_summaries(tenant_id, last_message_at DESC);


-- ═══════════════════════════════════════════════════════════════
-- B. Partition messages table by tenant_id
-- Enables partition pruning: queries with tenant_id = ? only scan
-- that tenant's partition. Critical for multi-tenant at scale.
-- ═══════════════════════════════════════════════════════════════

-- 1. Create new partitioned table
CREATE TABLE messages_partitioned (
    id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content TEXT,
    read BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id, tenant_id)
) PARTITION BY LIST (tenant_id);

-- 2. Create default partition (catches all tenants without explicit partitions)
CREATE TABLE messages_default PARTITION OF messages_partitioned DEFAULT;

-- 3. Create partition for tenant 1 (existing data)
CREATE TABLE messages_tenant_1 PARTITION OF messages_partitioned FOR VALUES IN (1);

-- 4. Copy existing data
INSERT INTO messages_partitioned (id, conversation_id, sender_id, content, read, created_at, tenant_id)
SELECT id, conversation_id, sender_id, content, read, created_at, tenant_id FROM messages;

-- 5. Drop old table and rename
DROP TABLE messages CASCADE;
ALTER TABLE messages_partitioned RENAME TO messages;

-- 6. Recreate indexes on the partitioned table
CREATE INDEX idx_messages_conv_time ON messages(conversation_id, created_at DESC);
CREATE INDEX idx_messages_sender ON messages(sender_id, created_at DESC);
CREATE INDEX idx_messages_tenant_conv_part ON messages(tenant_id, conversation_id, created_at DESC);

-- 7. Recreate message_attachments FK (was dropped by CASCADE)
-- Note: FKs referencing partitioned tables need to reference the partition key
-- So we skip the FK and rely on app-level integrity


-- ═══════════════════════════════════════════════════════════════
-- C. Add 2027 partitions for posts and comments
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE posts_2027_q1 PARTITION OF posts FOR VALUES FROM ('2027-01-01') TO ('2027-04-01');
CREATE TABLE posts_2027_q2 PARTITION OF posts FOR VALUES FROM ('2027-04-01') TO ('2027-07-01');
CREATE TABLE posts_2027_q3 PARTITION OF posts FOR VALUES FROM ('2027-07-01') TO ('2027-10-01');
CREATE TABLE posts_2027_q4 PARTITION OF posts FOR VALUES FROM ('2027-10-01') TO ('2028-01-01');

CREATE TABLE comments_2027_q1 PARTITION OF comments FOR VALUES FROM ('2027-01-01') TO ('2027-04-01');
CREATE TABLE comments_2027_q2 PARTITION OF comments FOR VALUES FROM ('2027-04-01') TO ('2027-07-01');
CREATE TABLE comments_2027_q3 PARTITION OF comments FOR VALUES FROM ('2027-07-01') TO ('2027-10-01');
CREATE TABLE comments_2027_q4 PARTITION OF comments FOR VALUES FROM ('2027-10-01') TO ('2028-01-01');
