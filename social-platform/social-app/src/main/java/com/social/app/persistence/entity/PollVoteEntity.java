package com.social.app.persistence.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "poll_votes")
@IdClass(PollVoteEntity.VoteId.class)
public class PollVoteEntity {

    @Id
    @Column(name = "poll_id")
    private Long pollId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "option_id")
    private Long optionId;

    @Column(name = "voted_at", nullable = false, updatable = false)
    private Instant votedAt;

    @PrePersist
    protected void onCreate() {
        if (votedAt == null) votedAt = Instant.now();
    }

    public Long getPollId() { return pollId; }
    public void setPollId(Long pollId) { this.pollId = pollId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getOptionId() { return optionId; }
    public void setOptionId(Long optionId) { this.optionId = optionId; }

    public Instant getVotedAt() { return votedAt; }
    public void setVotedAt(Instant votedAt) { this.votedAt = votedAt; }

    public static class VoteId implements Serializable {

        private Long pollId;
        private Long userId;
        private Long optionId;

        public VoteId() {}

        public VoteId(Long pollId, Long userId, Long optionId) {
            this.pollId = pollId;
            this.userId = userId;
            this.optionId = optionId;
        }

        public Long getPollId() { return pollId; }
        public void setPollId(Long pollId) { this.pollId = pollId; }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public Long getOptionId() { return optionId; }
        public void setOptionId(Long optionId) { this.optionId = optionId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VoteId voteId = (VoteId) o;
            return Objects.equals(pollId, voteId.pollId)
                    && Objects.equals(userId, voteId.userId)
                    && Objects.equals(optionId, voteId.optionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pollId, userId, optionId);
        }
    }
}
