-- Knowledge sets (one per page/group)
CREATE TABLE knowledge_sets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    slug VARCHAR(256) NOT NULL UNIQUE,
    description TEXT,
    social_page_id BIGINT,
    social_page_type VARCHAR(50),
    settings JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Documents within a knowledge set
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    knowledge_set_id BIGINT NOT NULL REFERENCES knowledge_sets(id) ON DELETE CASCADE,
    title VARCHAR(512) NOT NULL,
    source_url VARCHAR(2048),
    source_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    content TEXT NOT NULL,
    content_hash VARCHAR(64),
    metadata JSONB DEFAULT '{}',
    indexed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_ks ON documents(knowledge_set_id);
CREATE INDEX idx_documents_hash ON documents(content_hash);

-- Document chunks for RAG
CREATE TABLE document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    knowledge_set_id BIGINT NOT NULL REFERENCES knowledge_sets(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    token_count INT NOT NULL DEFAULT 0,
    embedding BYTEA,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chunks_doc ON document_chunks(document_id);
CREATE INDEX idx_chunks_ks ON document_chunks(knowledge_set_id);

-- Knowledge set links (for cross-referencing)
CREATE TABLE knowledge_set_links (
    source_id BIGINT NOT NULL REFERENCES knowledge_sets(id) ON DELETE CASCADE,
    target_id BIGINT NOT NULL REFERENCES knowledge_sets(id) ON DELETE CASCADE,
    link_type VARCHAR(50) NOT NULL DEFAULT 'RELATED',
    PRIMARY KEY (source_id, target_id)
);

-- Crawl jobs
CREATE TABLE crawl_jobs (
    id BIGSERIAL PRIMARY KEY,
    knowledge_set_id BIGINT NOT NULL REFERENCES knowledge_sets(id) ON DELETE CASCADE,
    start_url VARCHAR(2048) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    max_depth INT NOT NULL DEFAULT 3,
    max_pages INT NOT NULL DEFAULT 100,
    pages_crawled INT NOT NULL DEFAULT 0,
    pages_indexed INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Questions and interactions
CREATE TABLE interactions (
    id BIGSERIAL PRIMARY KEY,
    knowledge_set_id BIGINT REFERENCES knowledge_sets(id),
    question TEXT NOT NULL,
    answer TEXT,
    answer_source VARCHAR(50),
    confidence DOUBLE PRECISION,
    social_post_id BIGINT,
    social_comment_id BIGINT,
    social_user_id BIGINT,
    helpful BOOLEAN,
    escalated BOOLEAN NOT NULL DEFAULT false,
    support_case_id BIGINT,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_interactions_ks ON interactions(knowledge_set_id);

-- Captured solutions from community
CREATE TABLE captured_solutions (
    id BIGSERIAL PRIMARY KEY,
    knowledge_set_id BIGINT REFERENCES knowledge_sets(id),
    interaction_id BIGINT REFERENCES interactions(id),
    question TEXT NOT NULL,
    solution TEXT NOT NULL,
    source_user_id BIGINT,
    source_username VARCHAR(128),
    source_type VARCHAR(50) NOT NULL DEFAULT 'USER',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    promoted_to_document_id BIGINT REFERENCES documents(id),
    reviewer_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ
);
CREATE INDEX idx_captured_status ON captured_solutions(status);

-- Metrics
CREATE TABLE metrics (
    id BIGSERIAL PRIMARY KEY,
    knowledge_set_id BIGINT REFERENCES knowledge_sets(id),
    metric_type VARCHAR(100) NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    metadata JSONB DEFAULT '{}',
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_metrics_ks_type ON metrics(knowledge_set_id, metric_type, recorded_at);
