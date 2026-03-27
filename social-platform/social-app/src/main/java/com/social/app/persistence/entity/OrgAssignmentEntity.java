package com.social.app.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "org_assignments")
public class OrgAssignmentEntity {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "org_unit_id", nullable = false)
    private Long orgUnitId;

    @Column(name = "title", length = 128)
    private String title;

    @Column(name = "relationship_type", nullable = false, length = 20)
    private String relationshipType;

    @Column(name = "reports_to_user_id")
    private Long reportsToUserId;

    @Column(name = "level", length = 30)
    private String level;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (relationshipType == null) relationshipType = "SOLID";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getOrgUnitId() { return orgUnitId; }
    public void setOrgUnitId(Long orgUnitId) { this.orgUnitId = orgUnitId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }

    public Long getReportsToUserId() { return reportsToUserId; }
    public void setReportsToUserId(Long reportsToUserId) { this.reportsToUserId = reportsToUserId; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
