package com.social.app.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.Instant;

@Entity
@Table(name = "bot_memory")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class BotMemoryEntity {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "memory_key", nullable = false, length = 128)
    private String memoryKey;

    @Column(name = "memory_value", nullable = false, columnDefinition = "TEXT")
    private String memoryValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getMemoryKey() { return memoryKey; }
    public void setMemoryKey(String memoryKey) { this.memoryKey = memoryKey; }

    public String getMemoryValue() { return memoryValue; }
    public void setMemoryValue(String memoryValue) { this.memoryValue = memoryValue; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
