package com.social.app.service;

import com.social.app.persistence.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ConversationSummaryService {

    private final JdbcTemplate jdbc;
    private final UserRepository userRepo;

    public ConversationSummaryService(JdbcTemplate jdbc, UserRepository userRepo) {
        this.jdbc = jdbc;
        this.userRepo = userRepo;
    }

    /**
     * Update the denormalized conversation summary after every message send.
     */
    @Transactional
    public void updateSummary(long conversationId, long messageId, long senderId,
                               String content, Instant createdAt) {
        String senderName = userRepo.findById(senderId)
                .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                .orElse("Unknown");

        jdbc.update(
                "INSERT INTO conversation_summaries (conversation_id, last_message_id, last_message_at, " +
                "last_message_preview, last_sender_id, last_sender_name, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, now()) " +
                "ON CONFLICT (conversation_id) DO UPDATE SET " +
                "last_message_id = EXCLUDED.last_message_id, " +
                "last_message_at = EXCLUDED.last_message_at, " +
                "last_message_preview = EXCLUDED.last_message_preview, " +
                "last_sender_id = EXCLUDED.last_sender_id, " +
                "last_sender_name = EXCLUDED.last_sender_name, " +
                "updated_at = now()",
                conversationId, messageId, java.sql.Timestamp.from(createdAt),
                content != null ? content.substring(0, Math.min(content.length(), 100)) : "",
                senderId, senderName
        );
    }
}
