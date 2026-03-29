package com.social.app.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnreadCountService {

    private final JdbcTemplate jdbc;
    private final CacheService cache;

    public UnreadCountService(JdbcTemplate jdbc, CacheService cache) {
        this.jdbc = jdbc;
        this.cache = cache;
    }

    /**
     * Increment unread count for all participants in a conversation except the sender.
     */
    @Transactional
    public void incrementUnread(long conversationId, long senderId) {
        jdbc.update(
                "UPDATE unread_counts SET unread_count = unread_count + 1 " +
                "WHERE conversation_id = ? AND user_id != ?",
                conversationId, senderId
        );
        // Evict cache for affected users
        cache.evictPattern("unread:*");
    }

    /**
     * Reset unread count for a user in a specific conversation (on mark-read).
     */
    @Transactional
    public void resetUnread(long conversationId, long userId) {
        jdbc.update(
                "UPDATE unread_counts SET unread_count = 0 " +
                "WHERE conversation_id = ? AND user_id = ?",
                conversationId, userId
        );
        cache.evict("unread:" + userId);
    }

    /**
     * Get total unread message count for a user across all conversations (cached).
     */
    public int getTotalUnread(long userId) {
        return cache.get("unread:" + userId, Integer.class, () -> {
            Integer count = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(unread_count), 0) FROM unread_counts WHERE user_id = ?",
                    Integer.class, userId
            );
            return count != null ? count : 0;
        });
    }

    /**
     * Ensure an unread count record exists for a new conversation participant.
     */
    public void ensureRecord(long conversationId, long userId) {
        jdbc.update(
                "INSERT INTO unread_counts (user_id, conversation_id, unread_count) " +
                "VALUES (?, ?, 0) ON CONFLICT DO NOTHING",
                userId, conversationId
        );
    }
}
