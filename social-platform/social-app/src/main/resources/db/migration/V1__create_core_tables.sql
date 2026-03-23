-- Users
CREATE TABLE users (
    id              BIGINT PRIMARY KEY,
    username        VARCHAR(64) NOT NULL UNIQUE,
    display_name    VARCHAR(128),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255),
    avatar_url      VARCHAR(512),
    bio             TEXT,
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Teams
CREATE TABLE teams (
    id              BIGINT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    slug            VARCHAR(128) NOT NULL UNIQUE,
    description     TEXT,
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Groups (underscore to avoid SQL keyword)
CREATE TABLE groups_ (
    id              BIGINT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    slug            VARCHAR(128) NOT NULL UNIQUE,
    description     TEXT,
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Pages
CREATE TABLE pages (
    id              BIGINT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    slug            VARCHAR(128) NOT NULL UNIQUE,
    description     TEXT,
    avatar_url      VARCHAR(512),
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    owner_type      VARCHAR(20),
    owner_id        BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Projects
CREATE TABLE projects (
    id              BIGINT PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    slug            VARCHAR(128) NOT NULL UNIQUE,
    description     TEXT,
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    page_id         BIGINT REFERENCES pages(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Posts (partitioned by created_at)
CREATE TABLE posts (
    id              BIGINT NOT NULL,
    author_id       BIGINT NOT NULL REFERENCES users(id),
    target_type     VARCHAR(20),
    target_id       BIGINT,
    content         TEXT,
    visibility      VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE posts_2025_q1 PARTITION OF posts
    FOR VALUES FROM ('2025-01-01') TO ('2025-04-01');
CREATE TABLE posts_2025_q2 PARTITION OF posts
    FOR VALUES FROM ('2025-04-01') TO ('2025-07-01');
CREATE TABLE posts_2025_q3 PARTITION OF posts
    FOR VALUES FROM ('2025-07-01') TO ('2025-10-01');
CREATE TABLE posts_2025_q4 PARTITION OF posts
    FOR VALUES FROM ('2025-10-01') TO ('2026-01-01');
CREATE TABLE posts_2026_q1 PARTITION OF posts
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE posts_2026_q2 PARTITION OF posts
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE posts_2026_q3 PARTITION OF posts
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE posts_2026_q4 PARTITION OF posts
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

-- Comments (partitioned by created_at)
CREATE TABLE comments (
    id                  BIGINT NOT NULL,
    post_id             BIGINT NOT NULL,
    parent_comment_id   BIGINT,
    author_id           BIGINT NOT NULL REFERENCES users(id),
    content             TEXT,
    depth               SMALLINT NOT NULL DEFAULT 0 CHECK (depth <= 1),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE comments_2025_q1 PARTITION OF comments
    FOR VALUES FROM ('2025-01-01') TO ('2025-04-01');
CREATE TABLE comments_2025_q2 PARTITION OF comments
    FOR VALUES FROM ('2025-04-01') TO ('2025-07-01');
CREATE TABLE comments_2025_q3 PARTITION OF comments
    FOR VALUES FROM ('2025-07-01') TO ('2025-10-01');
CREATE TABLE comments_2025_q4 PARTITION OF comments
    FOR VALUES FROM ('2025-10-01') TO ('2026-01-01');
CREATE TABLE comments_2026_q1 PARTITION OF comments
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE comments_2026_q2 PARTITION OF comments
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE comments_2026_q3 PARTITION OF comments
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE comments_2026_q4 PARTITION OF comments
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

-- Reactions
CREATE TABLE reactions (
    id              BIGINT PRIMARY KEY,
    target_id       BIGINT NOT NULL,
    target_type     VARCHAR(10) NOT NULL,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    reaction_type   VARCHAR(10) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (target_id, user_id)
);

-- Attachments
CREATE TABLE attachments (
    id              BIGINT PRIMARY KEY,
    owner_id        BIGINT NOT NULL,
    media_type      VARCHAR(64),
    file_url        VARCHAR(512) NOT NULL,
    file_size       BIGINT,
    width           INT,
    height          INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Memberships
CREATE TABLE memberships (
    user_id         BIGINT NOT NULL REFERENCES users(id),
    group_id        BIGINT NOT NULL,
    role            VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, group_id)
);

-- Follows
CREATE TABLE follows (
    follower_id     BIGINT NOT NULL REFERENCES users(id),
    followed_id     BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followed_id)
);

-- Indexes
CREATE INDEX idx_posts_author_id ON posts (author_id);
CREATE INDEX idx_posts_target_id ON posts (target_id);
CREATE INDEX idx_comments_post_id ON comments (post_id);
CREATE INDEX idx_reactions_target_id ON reactions (target_id);
CREATE INDEX idx_attachments_owner_id ON attachments (owner_id);
CREATE INDEX idx_follows_followed_id ON follows (followed_id);
CREATE INDEX idx_memberships_group_id ON memberships (group_id);
