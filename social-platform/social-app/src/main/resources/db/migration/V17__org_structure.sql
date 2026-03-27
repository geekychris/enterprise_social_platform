-- Organizational units: Company → Division → Department → Team
CREATE TABLE org_units (
    id              BIGINT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    type            VARCHAR(30) NOT NULL,  -- COMPANY, DIVISION, DEPARTMENT, TEAM
    parent_id       BIGINT REFERENCES org_units(id),
    head_user_id    BIGINT REFERENCES users(id),
    description     TEXT,
    cost_center     VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_org_units_parent ON org_units (parent_id);
CREATE INDEX idx_org_units_head ON org_units (head_user_id);

-- User assignments to org units (supports multiple: solid + dotted lines)
CREATE TABLE org_assignments (
    id                   BIGINT PRIMARY KEY,
    user_id              BIGINT NOT NULL REFERENCES users(id),
    org_unit_id          BIGINT NOT NULL REFERENCES org_units(id),
    title                VARCHAR(128),
    relationship_type    VARCHAR(20) NOT NULL DEFAULT 'SOLID',  -- SOLID or DOTTED
    reports_to_user_id   BIGINT REFERENCES users(id),
    level                VARCHAR(30),  -- CEO, C_SUITE, VP, SVP, DIRECTOR, SENIOR_MANAGER, MANAGER, LEAD, SENIOR, MID, JUNIOR
    start_date           DATE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, org_unit_id, relationship_type)
);
CREATE INDEX idx_org_assignments_user ON org_assignments (user_id);
CREATE INDEX idx_org_assignments_unit ON org_assignments (org_unit_id);
CREATE INDEX idx_org_assignments_reports_to ON org_assignments (reports_to_user_id);
