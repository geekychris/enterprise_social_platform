package com.social.app.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.Instant;

@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "graph_edges",
        uniqueConstraints = @UniqueConstraint(columnNames = {"src_id", "edge_type", "dst_id"}),
        indexes = {
                @Index(name = "idx_graph_edges_src_type", columnList = "src_id, edge_type"),
                @Index(name = "idx_graph_edges_dst_type", columnList = "dst_id, edge_type")
        })
public class GraphEdgeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "src_id", nullable = false)
    private Long srcId;

    @Column(name = "edge_type", nullable = false, length = 50)
    private String edgeType;

    @Column(name = "dst_id", nullable = false)
    private Long dstId;

    @Column(name = "timestamp_ns", nullable = false)
    private Long timestampNs = 0L;

    @Column(name = "metadata")
    private Short metadata = 0;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSrcId() { return srcId; }
    public void setSrcId(Long srcId) { this.srcId = srcId; }

    public String getEdgeType() { return edgeType; }
    public void setEdgeType(String edgeType) { this.edgeType = edgeType; }

    public Long getDstId() { return dstId; }
    public void setDstId(Long dstId) { this.dstId = dstId; }

    public Long getTimestampNs() { return timestampNs; }
    public void setTimestampNs(Long timestampNs) { this.timestampNs = timestampNs; }

    public Short getMetadata() { return metadata; }
    public void setMetadata(Short metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
