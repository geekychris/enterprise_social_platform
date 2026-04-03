package com.aisupport.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final JdbcTemplate jdbc;
    private final OllamaService ollamaService;

    public AlertService(JdbcTemplate jdbc, OllamaService ollamaService) {
        this.jdbc = jdbc;
        this.ollamaService = ollamaService;
    }

    public void createAlert(String type, String severity, String message) {
        jdbc.update("INSERT INTO alerts (alert_type, severity, message) VALUES (?, ?, ?)",
                type, severity, message);
        log.warn("Alert [{}] {}: {}", severity, type, message);
    }

    public List<Map<String, Object>> getAlerts(boolean includeAcknowledged, int limit) {
        if (includeAcknowledged) {
            return jdbc.queryForList("SELECT * FROM alerts ORDER BY created_at DESC LIMIT ?", limit);
        }
        return jdbc.queryForList("SELECT * FROM alerts WHERE acknowledged = false ORDER BY created_at DESC LIMIT ?", limit);
    }

    public void acknowledge(long alertId) {
        jdbc.update("UPDATE alerts SET acknowledged = true WHERE id = ?", alertId);
    }

    public long getUnacknowledgedCount() {
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM alerts WHERE acknowledged = false", Long.class);
        return count != null ? count : 0;
    }

    /**
     * Periodic health check — creates alerts for issues.
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void healthCheck() {
        // Check Ollama availability
        if (!ollamaService.isAvailable()) {
            // Only alert if not already alerted recently
            var recent = jdbc.queryForList(
                "SELECT id FROM alerts WHERE alert_type = 'OLLAMA_DOWN' AND acknowledged = false AND created_at > now() - interval '5 minutes'");
            if (recent.isEmpty()) {
                createAlert("OLLAMA_DOWN", "CRITICAL", "Ollama is not responding. AI answers will not work.");
            }
        }

        // Check escalation rate (more than 50% in last hour)
        try {
            var total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM interactions WHERE created_at > now() - interval '1 hour'", Long.class);
            var escalated = jdbc.queryForObject(
                "SELECT COUNT(*) FROM interactions WHERE escalated = true AND created_at > now() - interval '1 hour'", Long.class);
            if (total != null && total > 5 && escalated != null) {
                double rate = (double) escalated / total;
                if (rate > 0.5) {
                    var recent = jdbc.queryForList(
                        "SELECT id FROM alerts WHERE alert_type = 'HIGH_ESCALATION' AND acknowledged = false AND created_at > now() - interval '1 hour'");
                    if (recent.isEmpty()) {
                        createAlert("HIGH_ESCALATION", "WARNING",
                                String.format("Escalation rate is %.0f%% (%d/%d) in the last hour", rate * 100, escalated, total));
                    }
                }
            }
        } catch (Exception e) {
            // Ignore — tables might not exist yet
        }
    }
}
