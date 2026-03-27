package com.social.app.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "polls")
public class PollEntity {

    @Id
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "question", nullable = false, length = 500)
    private String question;

    @Column(name = "allow_multiple", nullable = false)
    private boolean allowMultiple;

    @Column(name = "closes_at")
    private Instant closesAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public boolean isAllowMultiple() { return allowMultiple; }
    public void setAllowMultiple(boolean allowMultiple) { this.allowMultiple = allowMultiple; }

    public Instant getClosesAt() { return closesAt; }
    public void setClosesAt(Instant closesAt) { this.closesAt = closesAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
