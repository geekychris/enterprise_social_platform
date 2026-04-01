-- Multi-tenant support
-- Adds tenant_id to all entity tables. Existing data is assigned to tenant 1.

-- Tenants table
CREATE TABLE tenants (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    plan VARCHAR(50) NOT NULL DEFAULT 'free',
    max_users INT DEFAULT 1000,
    max_storage_gb INT DEFAULT 10,
    settings JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert default tenant for existing data
INSERT INTO tenants (id, name, slug, plan, max_users) VALUES (1, 'Default', 'default', 'enterprise', 100000);

-- Add tenant_id to all entity tables
ALTER TABLE users ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE groups_ ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE pages ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE teams ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE projects ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE follows ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE memberships ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE page_memberships ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE reactions ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE conversations ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE conversation_participants ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE messages ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE friend_requests ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE attachments ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE notifications ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE graph_edges ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE graph_entities ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE polls ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE poll_options ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE poll_votes ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE bot_memory ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE daily_digest_log ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE org_units ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE org_assignments ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE conversation_summaries ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE unread_counts ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE feed_entries ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE rate_limits ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE invite_tokens ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE platform_settings ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE bot_triggers ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE post_attachments ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE comment_attachments ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);
ALTER TABLE message_attachments ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id);

-- Partitioned tables: ALTER on parent propagates to all partitions
ALTER TABLE posts ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE comments ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
-- Note: FK constraints cannot be added to partitioned tables directly in PostgreSQL,
-- so we skip the REFERENCES clause for posts and comments.

-- Add indexes for tenant_id on high-traffic tables
CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_posts_tenant ON posts(tenant_id);
CREATE INDEX idx_comments_tenant ON comments(tenant_id);
CREATE INDEX idx_reactions_tenant ON reactions(tenant_id);
CREATE INDEX idx_messages_tenant ON messages(tenant_id);
CREATE INDEX idx_conversations_tenant ON conversations(tenant_id);
CREATE INDEX idx_follows_tenant ON follows(tenant_id);
CREATE INDEX idx_memberships_tenant ON memberships(tenant_id);
CREATE INDEX idx_groups_tenant ON groups_(tenant_id);
CREATE INDEX idx_feed_entries_tenant ON feed_entries(tenant_id);

-- Enable Row Level Security on critical tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;

-- RLS policies (the app user 'social' bypasses RLS by default as table owner,
-- but this protects against direct SQL injection or misconfigured connections)
CREATE POLICY tenant_isolation_users ON users USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_conversations ON conversations USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
CREATE POLICY tenant_isolation_messages ON messages USING (tenant_id = current_setting('app.tenant_id', true)::bigint);
