package com.social.app.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "memberships")
@IdClass(MembershipEntity.MembershipId.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class MembershipEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) joinedAt = Instant.now();
        if (role == null) role = "MEMBER";
        if (status == null) status = "APPROVED";
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public static class MembershipId implements Serializable {
        private Long userId;
        private Long groupId;

        public MembershipId() {}
        public MembershipId(Long userId, Long groupId) {
            this.userId = userId;
            this.groupId = groupId;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getGroupId() { return groupId; }
        public void setGroupId(Long groupId) { this.groupId = groupId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MembershipId that)) return false;
            return Objects.equals(userId, that.userId) && Objects.equals(groupId, that.groupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, groupId);
        }
    }
}
