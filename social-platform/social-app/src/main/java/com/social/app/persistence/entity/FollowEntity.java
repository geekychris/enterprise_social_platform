package com.social.app.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "follows")
@IdClass(FollowEntity.FollowId.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class FollowEntity {

    @Id
    @Column(name = "follower_id")
    private Long followerId;

    @Id
    @Column(name = "followed_id")
    private Long followedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getFollowerId() { return followerId; }
    public void setFollowerId(Long followerId) { this.followerId = followerId; }

    public Long getFollowedId() { return followedId; }
    public void setFollowedId(Long followedId) { this.followedId = followedId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public static class FollowId implements Serializable {
        private Long followerId;
        private Long followedId;

        public FollowId() {}
        public FollowId(Long followerId, Long followedId) {
            this.followerId = followerId;
            this.followedId = followedId;
        }

        public Long getFollowerId() { return followerId; }
        public void setFollowerId(Long followerId) { this.followerId = followerId; }
        public Long getFollowedId() { return followedId; }
        public void setFollowedId(Long followedId) { this.followedId = followedId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FollowId that)) return false;
            return Objects.equals(followerId, that.followerId) && Objects.equals(followedId, that.followedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(followerId, followedId);
        }
    }
}
