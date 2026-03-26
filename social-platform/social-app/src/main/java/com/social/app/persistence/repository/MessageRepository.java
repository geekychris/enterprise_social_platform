package com.social.app.persistence.repository;

import com.social.app.persistence.entity.MessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    List<MessageEntity> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    @Query("SELECT m FROM MessageEntity m WHERE m.conversationId = :conversationId AND m.createdAt >= :visibleFrom ORDER BY m.createdAt DESC")
    List<MessageEntity> findByConversationIdAndCreatedAtAfterOrderByCreatedAtDesc(Long conversationId, java.time.Instant visibleFrom, Pageable pageable);

    /**
     * Get the latest message for each of a user's conversations.
     */
    @Query(value = """
        SELECT DISTINCT ON (m.conversation_id) m.*
        FROM messages m
        JOIN conversation_participants cp ON cp.conversation_id = m.conversation_id AND cp.user_id = :userId
        ORDER BY m.conversation_id, m.created_at DESC
        """, nativeQuery = true)
    List<MessageEntity> findLatestPerConversation(Long userId);

    /**
     * Count unread messages across all conversations for a user.
     * A message is unread if it was sent by someone else and created after the user's last_read_at.
     */
    @Query(value = """
        SELECT COUNT(*) FROM messages m
        JOIN conversation_participants cp ON cp.conversation_id = m.conversation_id AND cp.user_id = :userId
        WHERE m.sender_id != :userId
          AND (cp.last_read_at IS NULL OR m.created_at > cp.last_read_at)
        """, nativeQuery = true)
    long countUnreadForUser(Long userId);

    /**
     * Count unread messages in a specific conversation for a user.
     */
    @Query(value = """
        SELECT COUNT(*) FROM messages m
        JOIN conversation_participants cp ON cp.conversation_id = m.conversation_id AND cp.user_id = :userId
        WHERE m.conversation_id = :conversationId
          AND m.sender_id != :userId
          AND (cp.last_read_at IS NULL OR m.created_at > cp.last_read_at)
        """, nativeQuery = true)
    long countUnreadInConversation(Long conversationId, Long userId);

    @Query("SELECT MAX(m.id) FROM MessageEntity m")
    Long findMaxId();
}
