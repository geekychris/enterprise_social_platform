package com.social.app.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.Instant;

@Entity
@Table(name = "org_units")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class OrgUnitEntity {

    @Id
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "head_user_id")
    private Long headUserId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "cost_center", length = 50)
    private String costCenter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public Long getHeadUserId() { return headUserId; }
    public void setHeadUserId(Long headUserId) { this.headUserId = headUserId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCostCenter() { return costCenter; }
    public void setCostCenter(String costCenter) { this.costCenter = costCenter; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
