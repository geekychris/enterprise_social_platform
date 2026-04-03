package com.aisupport.controller;

import com.aisupport.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;
    private final AlertService alertService;
    private final KnowledgeGapService gapService;
    private final AnswerCacheService cacheService;

    public MetricsController(MetricsService metricsService, AlertService alertService,
                              KnowledgeGapService gapService, AnswerCacheService cacheService) {
        this.metricsService = metricsService;
        this.alertService = alertService;
        this.gapService = gapService;
        this.cacheService = cacheService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok(metricsService.getDashboard());
    }

    @GetMapping("/methods")
    public ResponseEntity<List<Map<String, Object>>> methods() {
        return ResponseEntity.ok(metricsService.getMethodDistribution());
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> alerts(
            @RequestParam(defaultValue = "false") boolean all) {
        return ResponseEntity.ok(alertService.getAlerts(all, 50));
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public ResponseEntity<Void> ackAlert(@PathVariable long id) {
        alertService.acknowledge(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/gaps")
    public ResponseEntity<List<Map<String, Object>>> gaps(
            @RequestParam(required = false) Long knowledgeSetId) {
        return ResponseEntity.ok(gapService.getGaps(knowledgeSetId, 50));
    }

    @PostMapping("/gaps/{id}/resolve")
    public ResponseEntity<Void> resolveGap(@PathVariable long id,
                                            @RequestBody Map<String, Object> body) {
        Long docId = body.get("documentId") != null ? Long.parseLong(body.get("documentId").toString()) : null;
        gapService.resolveGap(id, docId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/cache")
    public ResponseEntity<Map<String, Object>> cacheStats() {
        return ResponseEntity.ok(cacheService.getStats());
    }
}
