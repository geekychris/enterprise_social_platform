package com.aisupport.service;

import com.aisupport.search.UnifiedSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AnswerCacheService {
    private static final Logger log = LoggerFactory.getLogger(AnswerCacheService.class);
    private static final double SIMILARITY_THRESHOLD = 0.92;

    private final JdbcTemplate jdbc;
    private final OllamaService ollamaService;

    public AnswerCacheService(JdbcTemplate jdbc, OllamaService ollamaService) {
        this.jdbc = jdbc;
        this.ollamaService = ollamaService;
    }

    public record CachedAnswer(String answer, double confidence, String citations, long cacheId) {}

    /**
     * Check cache for a similar question. Returns null if no match.
     */
    public CachedAnswer lookup(long knowledgeSetId, String question) {
        try {
            String hash = sha256(question.toLowerCase().trim());
            // Exact match first
            var exact = jdbc.queryForList(
                "SELECT id, answer, confidence, citations FROM answer_cache WHERE knowledge_set_id = ? AND question_hash = ? AND (expires_at IS NULL OR expires_at > now()) LIMIT 1",
                knowledgeSetId, hash);
            if (!exact.isEmpty()) {
                var row = exact.get(0);
                jdbc.update("UPDATE answer_cache SET hit_count = hit_count + 1 WHERE id = ?", row.get("id"));
                log.info("Cache HIT (exact) for ks-{}: {}", knowledgeSetId, question.substring(0, Math.min(50, question.length())));
                Object citationsObj = row.get("citations");
                String citationsStr = citationsObj != null ? citationsObj.toString() : "[]";
                return new CachedAnswer((String) row.get("answer"), ((Number) row.get("confidence")).doubleValue(),
                        citationsStr, ((Number) row.get("id")).longValue());
            }
            // Semantic similarity match — check recent cached answers
            // (In production, you'd store embeddings and do KNN. For now, we skip semantic cache lookup
            //  as the LLM call latency is acceptable and freshness matters.)
            return null;
        } catch (Exception e) {
            log.error("Cache lookup FAILED: {} ({})", e.getMessage(), e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Store an answer in the cache.
     */
    public void store(long knowledgeSetId, String question, String answer, double confidence, String citationsJson) {
        try {
            String hash = sha256(question.toLowerCase().trim());
            jdbc.update(
                "INSERT INTO answer_cache (knowledge_set_id, question_hash, question, answer, confidence, citations) VALUES (?, ?, ?, ?, ?, ?::jsonb) ON CONFLICT DO NOTHING",
                knowledgeSetId, hash, question, answer, confidence, citationsJson != null ? citationsJson : "[]");
        } catch (Exception e) {
            log.debug("Cache store failed: {}", e.getMessage());
        }
    }

    public void invalidate(long knowledgeSetId) {
        jdbc.update("DELETE FROM answer_cache WHERE knowledge_set_id = ?", knowledgeSetId);
        log.info("Invalidated answer cache for ks-{}", knowledgeSetId);
    }

    public Map<String, Object> getStats() {
        var total = jdbc.queryForObject("SELECT COUNT(*) FROM answer_cache", Long.class);
        var hits = jdbc.queryForObject("SELECT COALESCE(SUM(hit_count), 0) FROM answer_cache", Long.class);
        return Map.of("totalCached", total != null ? total : 0, "totalHits", hits != null ? hits : 0);
    }

    private static String sha256(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }
}
