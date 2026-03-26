package com.social.app.persistence.repository;

import com.social.app.persistence.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {

    @Query("SELECT MAX(c.id) FROM ConversationEntity c")
    Long findMaxId();

    /**
     * Find an existing DIRECT conversation between exactly two users.
     */
    @Query(value = """
        SELECT c.* FROM conversations c
        WHERE c.type = 'DIRECT'
          AND EXISTS (SELECT 1 FROM conversation_participants cp WHERE cp.conversation_id = c.id AND cp.user_id = :user1)
          AND EXISTS (SELECT 1 FROM conversation_participants cp WHERE cp.conversation_id = c.id AND cp.user_id = :user2)
          AND (SELECT COUNT(*) FROM conversation_participants cp WHERE cp.conversation_id = c.id) = 2
        LIMIT 1
        """, nativeQuery = true)
    Optional<ConversationEntity> findDirectConversation(Long user1, Long user2);

    /**
     * Find all conversations a user participates in, ordered by latest message time.
     */
    @Query(value = """
        SELECT c.* FROM conversations c
        JOIN conversation_participants cp ON cp.conversation_id = c.id
        WHERE cp.user_id = :userId
        ORDER BY (
            SELECT MAX(m.created_at) FROM messages m WHERE m.conversation_id = c.id
        ) DESC NULLS LAST
        """, nativeQuery = true)
    List<ConversationEntity> findUserConversations(Long userId);
}
