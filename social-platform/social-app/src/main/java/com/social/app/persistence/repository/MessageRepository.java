package com.social.app.persistence.repository;

import com.social.app.persistence.entity.MessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    @Query("SELECT m FROM MessageEntity m WHERE " +
           "(m.senderId = :user1 AND m.recipientId = :user2) OR " +
           "(m.senderId = :user2 AND m.recipientId = :user1) " +
           "ORDER BY m.createdAt DESC")
    List<MessageEntity> findConversation(Long user1, Long user2, Pageable pageable);

    @Query(value = "SELECT DISTINCT ON (partner_id) * FROM (" +
           "SELECT m.*, CASE WHEN m.sender_id = :userId THEN m.recipient_id ELSE m.sender_id END AS partner_id " +
           "FROM messages m WHERE m.sender_id = :userId OR m.recipient_id = :userId" +
           ") sub ORDER BY partner_id, created_at DESC", nativeQuery = true)
    List<MessageEntity> findLatestPerConversation(Long userId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    @Modifying
    @Query("UPDATE MessageEntity m SET m.read = true WHERE m.senderId = :senderId AND m.recipientId = :recipientId AND m.read = false")
    void markConversationRead(Long senderId, Long recipientId);

    @Query("SELECT MAX(m.id) FROM MessageEntity m")
    Long findMaxId();
}
