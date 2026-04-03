package com.aisupport.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MetricsService {
    private final JdbcTemplate jdbc;

    public MetricsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(Long knowledgeSetId, String metricType, double value) {
        jdbc.update("INSERT INTO metrics (knowledge_set_id, metric_type, metric_value) VALUES (?, ?, ?)",
                knowledgeSetId, metricType, value);
    }

    public Map<String, Object> getDashboard() {
        Map<String, Object> dashboard = new LinkedHashMap<>();

        // Overall stats
        dashboard.put("totalInteractions", queryLong("SELECT COUNT(*) FROM interactions"));
        dashboard.put("totalInteractions24h", queryLong("SELECT COUNT(*) FROM interactions WHERE created_at > now() - interval '24 hours'"));
        dashboard.put("totalEscalated", queryLong("SELECT COUNT(*) FROM interactions WHERE escalated = true"));
        dashboard.put("avgConfidence", queryDouble("SELECT AVG(confidence) FROM interactions WHERE confidence IS NOT NULL"));
        dashboard.put("totalDocuments", queryLong("SELECT COUNT(*) FROM documents WHERE status = 'ACTIVE'"));
        dashboard.put("totalChunks", queryLong("SELECT COUNT(*) FROM document_chunks"));

        // Response time
        dashboard.put("avgResponseMs", queryDouble("SELECT AVG(llm_duration_ms) FROM qa_traces WHERE llm_duration_ms IS NOT NULL"));
        dashboard.put("p95ResponseMs", queryDouble("SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY llm_duration_ms) FROM qa_traces WHERE llm_duration_ms IS NOT NULL"));

        // Escalation rate
        var total = queryLong("SELECT COUNT(*) FROM interactions WHERE created_at > now() - interval '7 days'");
        var escalated = queryLong("SELECT COUNT(*) FROM interactions WHERE escalated = true AND created_at > now() - interval '7 days'");
        dashboard.put("escalationRate7d", total > 0 ? Math.round((double) escalated / total * 100) : 0);

        // Positive feedback rate
        var rated = queryLong("SELECT COUNT(*) FROM interactions WHERE rating IS NOT NULL");
        var positive = queryLong("SELECT COUNT(*) FROM interactions WHERE rating >= 4");
        dashboard.put("positiveRatingRate", rated > 0 ? Math.round((double) positive / rated * 100) : 0);

        // Per-KS breakdown
        var perKs = jdbc.queryForList("""
            SELECT ks.id, ks.name,
                COUNT(i.id) as question_count,
                AVG(i.confidence) as avg_confidence,
                COUNT(CASE WHEN i.escalated THEN 1 END) as escalated_count,
                COUNT(CASE WHEN i.rating >= 4 THEN 1 END) as positive_count
            FROM knowledge_sets ks
            LEFT JOIN interactions i ON i.knowledge_set_id = ks.id
            GROUP BY ks.id, ks.name ORDER BY question_count DESC
        """);
        dashboard.put("perKnowledgeSet", perKs);

        // Daily activity (last 14 days)
        var daily = jdbc.queryForList("""
            SELECT date_trunc('day', created_at)::date as day,
                COUNT(*) as questions,
                AVG(confidence) as avg_conf,
                COUNT(CASE WHEN escalated THEN 1 END) as escalations
            FROM interactions WHERE created_at > now() - interval '14 days'
            GROUP BY day ORDER BY day
        """);
        dashboard.put("dailyActivity", daily);

        // Cache stats
        var cacheTotal = queryLong("SELECT COUNT(*) FROM answer_cache");
        var cacheHits = queryLong("SELECT COALESCE(SUM(hit_count), 0) FROM answer_cache");
        dashboard.put("cacheEntries", cacheTotal);
        dashboard.put("cacheHits", cacheHits);

        // Knowledge gaps
        var gaps = queryLong("SELECT COUNT(*) FROM knowledge_gaps WHERE status = 'OPEN'");
        dashboard.put("openKnowledgeGaps", gaps);

        // Alerts
        var alerts = queryLong("SELECT COUNT(*) FROM alerts WHERE acknowledged = false");
        dashboard.put("unacknowledgedAlerts", alerts);

        return dashboard;
    }

    /**
     * Get method distribution (AGENTIC vs RAG vs DIRECT_CONTEXT).
     */
    public List<Map<String, Object>> getMethodDistribution() {
        return jdbc.queryForList("""
            SELECT method, COUNT(*) as count, AVG(confidence) as avg_confidence,
                AVG(llm_duration_ms) as avg_duration
            FROM qa_traces WHERE created_at > now() - interval '30 days'
            GROUP BY method ORDER BY count DESC
        """);
    }

    private long queryLong(String sql) {
        try {
            var result = jdbc.queryForObject(sql, Long.class);
            return result != null ? result : 0;
        } catch (Exception e) { return 0; }
    }

    private double queryDouble(String sql) {
        try {
            var result = jdbc.queryForObject(sql, Double.class);
            return result != null ? Math.round(result * 100) / 100.0 : 0;
        } catch (Exception e) { return 0; }
    }
}
