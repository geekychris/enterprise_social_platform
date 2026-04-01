package com.social.app.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.Instant;

@Entity
@Table(name = "app_installations")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class AppInstallationEntity {

    @Id
    private Long id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "install_type", nullable = false, length = 20)
    private String installType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "installed_by", nullable = false)
    private Long installedBy;

    @Column(name = "config", columnDefinition = "JSONB")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String config;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (active == null) active = true;
        if (config == null) config = "{}";
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getInstallType() { return installType; }
    public void setInstallType(String installType) { this.installType = installType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public Long getInstalledBy() { return installedBy; }
    public void setInstalledBy(Long installedBy) { this.installedBy = installedBy; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
