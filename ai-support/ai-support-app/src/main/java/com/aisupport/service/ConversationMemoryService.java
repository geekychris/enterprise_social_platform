package com.aisupport.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ConversationMemoryService {
    private final JdbcTemplate jdbc;

    public ConversationMemoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Get recent conversation history for a social post thread.
     * Returns previous Q&A pairs for context.
     */
    public List<Map<String, Object>> getConversationHistory(Long socialPostId, int maxTurns) {
        if (socialPostId == null) return List.of();
        return jdbc.queryForList(
            "SELECT question, answer, confidence FROM interactions WHERE social_post_id = ? ORDER BY created_at DESC LIMIT ?",
            socialPostId, maxTurns);
    }

    /**
     * Build a conversation context string from history.
     */
    public String buildConversationContext(Long socialPostId) {
        var history = getConversationHistory(socialPostId, 3);
        if (history.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("Previous conversation on this thread:\n\n");
        // Reverse to chronological order
        Collections.reverse(history);
        for (var turn : history) {
            sb.append("User: ").append(turn.get("question")).append("\n");
            String answer = (String) turn.get("answer");
            if (answer != null && answer.length() > 300) {
                answer = answer.substring(0, 300) + "...";
            }
            sb.append("AI: ").append(answer).append("\n\n");
        }
        return sb.toString();
    }
}
