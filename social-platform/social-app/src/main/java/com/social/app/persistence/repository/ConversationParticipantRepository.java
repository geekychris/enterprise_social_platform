package com.social.app.persistence.repository;

import com.social.app.persistence.entity.ConversationParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipantEntity, ConversationParticipantEntity.ParticipantId> {

    List<ConversationParticipantEntity> findByConversationId(Long conversationId);

    List<ConversationParticipantEntity> findByUserId(Long userId);

    Optional<ConversationParticipantEntity> findByConversationIdAndUserId(Long conversationId, Long userId);

    @Modifying
    @Query("UPDATE ConversationParticipantEntity cp SET cp.lastReadAt = :readAt " +
           "WHERE cp.conversationId = :conversationId AND cp.userId = :userId")
    void updateLastReadAt(Long conversationId, Long userId, Instant readAt);
}
