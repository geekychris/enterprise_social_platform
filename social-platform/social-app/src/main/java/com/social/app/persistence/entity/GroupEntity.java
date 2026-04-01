package com.social.app.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.Instant;

@Entity
@Table(name = "groups_")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class GroupEntity {

    @Id
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 128)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "cover_url", length = 512)
    private String coverUrl;

    @Column(name = "pinned_post_id")
    private Long pinnedPostId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (visibility == null) visibility = "PUBLIC";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public Long getPinnedPostId() { return pinnedPostId; }
    public void setPinnedPostId(Long pinnedPostId) { this.pinnedPostId = pinnedPostId; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
