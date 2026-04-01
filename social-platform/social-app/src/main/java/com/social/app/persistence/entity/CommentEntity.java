package com.social.app.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import java.time.Instant;

@Entity
@Table(name = "comments")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class CommentEntity {

    @Id
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "depth", nullable = false)
    private Short depth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (depth == null) depth = 0;
    }

    // Getters and setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }

    public Long getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(Long parentCommentId) { this.parentCommentId = parentCommentId; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Short getDepth() { return depth; }
    public void setDepth(Short depth) { this.depth = depth; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}
