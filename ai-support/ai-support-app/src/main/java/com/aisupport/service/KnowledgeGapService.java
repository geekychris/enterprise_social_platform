package com.aisupport.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class KnowledgeGapService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeGapService.class);

    private final JdbcTemplate jdbc;

    public KnowledgeGapService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Record a low-confidence question as a knowledge gap.
     */
    public void recordGap(long knowledgeSetId, String question) {
        try {
            // Check if similar gap exists (exact match on normalized question)
            String normalized = question.toLowerCase().trim().replaceAll("\\s+", " ");
            var existing = jdbc.queryForList(
                "SELECT id FROM knowledge_gaps WHERE knowledge_set_id = ? AND LOWER(question) = ? AND status = 'OPEN'",
                knowledgeSetId, normalized);

            if (!existing.isEmpty()) {
                jdbc.update("UPDATE knowledge_gaps SET frequency = frequency + 1, last_asked_at = now() WHERE id = ?",
                        existing.get(0).get("id"));
            } else {
                jdbc.update("INSERT INTO knowledge_gaps (knowledge_set_id, question) VALUES (?, ?)",
                        knowledgeSetId, question);
            }
        } catch (Exception e) {
            log.debug("Failed to record knowledge gap: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getGaps(Long knowledgeSetId, int limit) {
        if (knowledgeSetId != null) {
            return jdbc.queryForList(
                "SELECT * FROM knowledge_gaps WHERE knowledge_set_id = ? AND status = 'OPEN' ORDER BY frequency DESC, last_asked_at DESC LIMIT ?",
                knowledgeSetId, limit);
        }
        return jdbc.queryForList(
            "SELECT * FROM knowledge_gaps WHERE status = 'OPEN' ORDER BY frequency DESC, last_asked_at DESC LIMIT ?",
            limit);
    }

    public void resolveGap(long gapId, Long documentId) {
        jdbc.update("UPDATE knowledge_gaps SET status = 'RESOLVED', resolved_by_document_id = ? WHERE id = ?",
                documentId, gapId);
    }

    public Map<String, Object> getStats(Long knowledgeSetId) {
        String where = knowledgeSetId != null ? " WHERE knowledge_set_id = " + knowledgeSetId : "";
        var open = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_gaps" + where + " AND status = 'OPEN'", Long.class);
        var resolved = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_gaps" + where + " AND status = 'RESOLVED'", Long.class);
        return Map.of("openGaps", open != null ? open : 0, "resolvedGaps", resolved != null ? resolved : 0);
    }
}
