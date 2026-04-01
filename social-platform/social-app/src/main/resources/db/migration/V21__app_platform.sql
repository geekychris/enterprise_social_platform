-- App Platform: Apps, Installations, Events, Cases

-- App registry
CREATE TABLE apps (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    description TEXT,
    icon_url VARCHAR(500),
    webhook_url VARCHAR(500) NOT NULL,
    api_key_hash VARCHAR(255) NOT NULL,
    app_type VARCHAR(20) NOT NULL DEFAULT 'PAGE',  -- PAGE, USER, ORG
    permissions JSONB NOT NULL DEFAULT '[]',
    settings JSONB DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT true,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, slug)
);

-- App installations (app installed on a page, group, user, or org-wide)
CREATE TABLE app_installations (
    id BIGINT PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES apps(id),
    tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id),
    install_type VARCHAR(20) NOT NULL,  -- PAGE, GROUP, USER, ORG
    target_id BIGINT NOT NULL,          -- page/group/user/org ID
    installed_by BIGINT NOT NULL,
    config JSONB DEFAULT '{}',
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_installs_app ON app_installations(app_id, active);
CREATE INDEX idx_app_installs_target ON app_installations(install_type, target_id, active);
CREATE INDEX idx_app_installs_tenant ON app_installations(tenant_id);

-- Event queue (per-app, with retry)
CREATE TABLE app_events (
    id BIGINT PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES apps(id),
    installation_id BIGINT REFERENCES app_installations(id),
    tenant_id BIGINT NOT NULL DEFAULT 1,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, DELIVERED, FAILED, DEAD
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    delivered_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_events_delivery ON app_events(app_id, status, next_retry_at);
CREATE INDEX idx_app_events_app_pending ON app_events(app_id, status) WHERE status = 'PENDING';

-- Support cases (generic case management)
CREATE TABLE support_cases (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1 REFERENCES tenants(id),
    app_id BIGINT REFERENCES apps(id),
    installation_id BIGINT REFERENCES app_installations(id),
    case_number VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED
    priority VARCHAR(20) DEFAULT 'NORMAL',  -- LOW, NORMAL, HIGH, URGENT
    requester_id BIGINT NOT NULL,
    assignee_id BIGINT,
    source_post_id BIGINT,
    source_comment_id BIGINT,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP
);

CREATE INDEX idx_cases_tenant_status ON support_cases(tenant_id, status);
CREATE INDEX idx_cases_assignee ON support_cases(assignee_id, status);
CREATE INDEX idx_cases_app ON support_cases(app_id, status);
CREATE SEQUENCE case_number_seq START WITH 1000;
