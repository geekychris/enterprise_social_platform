package com.social.app.persistence.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "page_memberships")
@IdClass(PageMembershipEntity.PageMembershipId.class)
public class PageMembershipEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "page_id")
    private Long pageId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) joinedAt = Instant.now();
        if (role == null) role = "FOLLOWER";
        if (status == null) status = "APPROVED";
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public static class PageMembershipId implements Serializable {
        private Long userId;
        private Long pageId;

        public PageMembershipId() {}
        public PageMembershipId(Long userId, Long pageId) {
            this.userId = userId;
            this.pageId = pageId;
        }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getPageId() { return pageId; }
        public void setPageId(Long pageId) { this.pageId = pageId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PageMembershipId that)) return false;
            return Objects.equals(userId, that.userId) && Objects.equals(pageId, that.pageId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, pageId);
        }
    }
}
