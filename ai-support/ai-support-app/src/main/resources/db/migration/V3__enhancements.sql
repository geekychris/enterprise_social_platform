-- Document versioning
ALTER TABLE documents ADD COLUMN version INT NOT NULL DEFAULT 1;
ALTER TABLE documents ADD COLUMN parent_document_id BIGINT REFERENCES documents(id);
ALTER TABLE documents ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

-- Auto-refresh scheduling for crawl sources
ALTER TABLE knowledge_sets ADD COLUMN refresh_cron VARCHAR(100);
ALTER TABLE knowledge_sets ADD COLUMN last_refreshed_at TIMESTAMPTZ;

-- Answer cache
CREATE TABLE answer_cache (
    id BIGSERIAL PRIMARY KEY,
    knowledge_set_id BIGINT NOT NULL REFERENCES knowledge_sets(id) ON DELETE CASCADE,
    question_hash VARCHAR(64) NOT NULL,
    question TEXT NOT NULL,
    question_embedding BYTEA,
    answer TEXT NOT NULL,
    confidence DOUBLE PRECISION,
    citations JSONB DEFAULT '[]',
    hit_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ
);
CREATE INDEX idx_answer_cache_ks_hash ON answer_cache(knowledge_set_id, question_hash);

-- Feedback / ratings
ALTER TABLE interactions ADD COLUMN rating INT;
ALTER TABLE interactions ADD COLUMN rating_comment TEXT;
ALTER TABLE interactions ADD COLUMN strategy VARCHAR(50);

-- Knowledge gaps
CREATE TABLE knowledge_gaps (
    id BIGSERIAL PRIMARY KEY,
    knowledge_set_id BIGINT REFERENCES knowledge_sets(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    frequency INT NOT NULL DEFAULT 1,
    last_asked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolved_by_document_id BIGINT REFERENCES documents(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_knowledge_gaps_ks ON knowledge_gaps(knowledge_set_id, status);

-- Alerts
CREATE TABLE alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    message TEXT NOT NULL,
    acknowledged BOOLEAN NOT NULL DEFAULT false,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_alerts_ack ON alerts(acknowledged, created_at DESC);

-- Conversation memory
ALTER TABLE interactions ADD COLUMN conversation_id VARCHAR(100);
ALTER TABLE interactions ADD COLUMN parent_interaction_id BIGINT REFERENCES interactions(id);

-- A/B test tracking
ALTER TABLE interactions ADD COLUMN ab_variant VARCHAR(50);

-- Grounding score
ALTER TABLE qa_traces ADD COLUMN grounding_score DOUBLE PRECISION;
ALTER TABLE qa_traces ADD COLUMN grounding_details JSONB DEFAULT '{}';
