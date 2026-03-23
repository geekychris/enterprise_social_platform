CREATE TABLE graph_edges (
    id BIGSERIAL PRIMARY KEY,
    src_id BIGINT NOT NULL,
    edge_type VARCHAR(50) NOT NULL,
    dst_id BIGINT NOT NULL,
    timestamp_ns BIGINT NOT NULL DEFAULT 0,
    metadata SMALLINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(src_id, edge_type, dst_id)
);

CREATE INDEX idx_graph_edges_src_type ON graph_edges(src_id, edge_type);
CREATE INDEX idx_graph_edges_dst_type ON graph_edges(dst_id, edge_type);

CREATE TABLE graph_entities (
    id BIGINT PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    name VARCHAR(256),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_graph_entities_type ON graph_entities(entity_type);
