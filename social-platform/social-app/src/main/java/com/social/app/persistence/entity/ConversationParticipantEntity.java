package com.social.app.persistence.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "conversation_participants")
@IdClass(ConversationParticipantEntity.ParticipantId.class)
public class ConversationParticipantEntity {

    @Id
    @Column(name = "conversation_id")
    private Long conversationId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "visible_from")
    private Instant visibleFrom;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) joinedAt = Instant.now();
    }

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public Instant getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(Instant lastReadAt) { this.lastReadAt = lastReadAt; }

    public Instant getVisibleFrom() { return visibleFrom; }
    public void setVisibleFrom(Instant visibleFrom) { this.visibleFrom = visibleFrom; }

    public static class ParticipantId implements Serializable {
        private Long conversationId;
        private Long userId;

        public ParticipantId() {}
        public ParticipantId(Long conversationId, Long userId) {
            this.conversationId = conversationId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParticipantId that)) return false;
            return Objects.equals(conversationId, that.conversationId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(conversationId, userId);
        }
    }
}
