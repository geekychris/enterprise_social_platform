CREATE TABLE qa_traces (
    id BIGSERIAL PRIMARY KEY,
    interaction_id BIGINT REFERENCES interactions(id),
    knowledge_set_id BIGINT REFERENCES knowledge_sets(id),
    question TEXT NOT NULL,
    method VARCHAR(50) NOT NULL,
    confidence DOUBLE PRECISION,

    -- Search step
    lexical_query TEXT,
    lexical_results JSONB DEFAULT '[]',
    semantic_query TEXT,
    semantic_results JSONB DEFAULT '[]',
    merged_results JSONB DEFAULT '[]',

    -- Context step
    context_chunks JSONB DEFAULT '[]',
    context_token_count INT DEFAULT 0,
    total_knowledge_tokens BIGINT DEFAULT 0,

    -- LLM step
    system_prompt TEXT,
    user_prompt TEXT,
    llm_model VARCHAR(100),
    llm_response TEXT,
    llm_duration_ms BIGINT,

    -- Answer
    answer TEXT,
    citations JSONB DEFAULT '[]',
    suggest_human BOOLEAN DEFAULT false,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_qa_traces_ks ON qa_traces(knowledge_set_id, created_at DESC);
CREATE INDEX idx_qa_traces_interaction ON qa_traces(interaction_id);
