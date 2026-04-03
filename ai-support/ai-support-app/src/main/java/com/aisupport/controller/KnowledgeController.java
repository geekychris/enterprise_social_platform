package com.aisupport.controller;

import com.aisupport.crawler.WebCrawlerService;
import com.aisupport.persistence.entity.KnowledgeSetEntity;
import com.aisupport.service.KnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final WebCrawlerService crawlerService;

    public KnowledgeController(KnowledgeService knowledgeService, WebCrawlerService crawlerService) {
        this.knowledgeService = knowledgeService;
        this.crawlerService = crawlerService;
    }

    // ── Knowledge Sets ───────────────────────────────────────

    @GetMapping("/sets")
    public ResponseEntity<List<Map<String, Object>>> listSets() {
        var sets = knowledgeService.getAllKnowledgeSets();
        return ResponseEntity.ok(sets.stream().map(this::toSetDto).collect(Collectors.toList()));
    }

    @PostMapping("/sets")
    public ResponseEntity<Map<String, Object>> createSet(@RequestBody Map<String, Object> body) {
        var ks = knowledgeService.createKnowledgeSet(
                (String) body.get("name"),
                (String) body.get("slug"),
                (String) body.get("description"),
                body.get("socialPageId") != null ? Long.parseLong(body.get("socialPageId").toString()) : null,
                (String) body.get("socialPageType")
        );
        return ResponseEntity.ok(toSetDto(ks));
    }

    @PutMapping("/sets/{id}")
    public ResponseEntity<Map<String, Object>> updateSet(@PathVariable long id, @RequestBody Map<String, Object> body) {
        var ks = knowledgeService.getKnowledgeSet(id).orElse(null);
        if (ks == null) return ResponseEntity.notFound().build();
        if (body.containsKey("name")) ks.setName((String) body.get("name"));
        if (body.containsKey("description")) ks.setDescription((String) body.get("description"));
        if (body.containsKey("socialPageId")) {
            Object v = body.get("socialPageId");
            ks.setSocialPageId(v != null ? Long.parseLong(v.toString()) : null);
        }
        if (body.containsKey("socialPageType")) ks.setSocialPageType((String) body.get("socialPageType"));
        knowledgeService.saveKnowledgeSet(ks);
        return ResponseEntity.ok(toSetDto(ks));
    }

    @GetMapping("/sets/{id}")
    public ResponseEntity<Map<String, Object>> getSet(@PathVariable long id) {
        return knowledgeService.getKnowledgeSet(id)
                .map(ks -> {
                    var dto = toSetDto(ks);
                    dto.putAll(knowledgeService.getStats(id));
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Documents ────────────────────────────────────────────

    @GetMapping("/sets/{ksId}/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments(@PathVariable long ksId) {
        var docs = knowledgeService.getDocuments(ksId);
        return ResponseEntity.ok(docs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("title", d.getTitle());
            m.put("sourceUrl", d.getSourceUrl());
            m.put("sourceType", d.getSourceType());
            m.put("indexed", d.isIndexed());
            m.put("contentLength", d.getContent() != null ? d.getContent().length() : 0);
            m.put("createdAt", d.getCreatedAt());
            return m;
        }).collect(Collectors.toList()));
    }

    @PostMapping("/sets/{ksId}/documents")
    public ResponseEntity<Map<String, Object>> addDocument(@PathVariable long ksId,
                                                            @RequestBody Map<String, Object> body) {
        var doc = knowledgeService.addDocument(ksId,
                (String) body.get("title"),
                (String) body.get("content"),
                (String) body.get("sourceUrl"),
                (String) body.get("sourceType")
        );

        // Auto-index
        int indexed = knowledgeService.indexDocument(doc.getId());

        return ResponseEntity.ok(Map.of(
                "id", doc.getId(),
                "title", doc.getTitle(),
                "indexed", doc.isIndexed(),
                "chunksIndexed", indexed
        ));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable long id) {
        knowledgeService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sets/{ksId}/index-all")
    public ResponseEntity<Map<String, String>> indexAll(@PathVariable long ksId) {
        knowledgeService.indexAllUnindexed(ksId);
        return ResponseEntity.ok(Map.of("status", "indexing_started"));
    }

    // ── Crawling ─────────────────────────────────────────────

    @PostMapping("/sets/{ksId}/crawl")
    public ResponseEntity<Map<String, Object>> startCrawl(@PathVariable long ksId,
                                                           @RequestBody Map<String, Object> body) {
        var job = crawlerService.createJob(ksId,
                (String) body.get("url"),
                body.get("maxDepth") != null ? ((Number) body.get("maxDepth")).intValue() : null,
                body.get("maxPages") != null ? ((Number) body.get("maxPages")).intValue() : null
        );

        // Start async crawl
        crawlerService.executeCrawl(job.getId());

        return ResponseEntity.ok(Map.of(
                "jobId", job.getId(),
                "status", job.getStatus()
        ));
    }

    // ── Helpers ──────────────────────────────────────────────

    private Map<String, Object> toSetDto(KnowledgeSetEntity ks) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ks.getId());
        m.put("name", ks.getName());
        m.put("slug", ks.getSlug());
        m.put("description", ks.getDescription());
        m.put("socialPageId", ks.getSocialPageId());
        m.put("socialPageType", ks.getSocialPageType());
        m.put("createdAt", ks.getCreatedAt());
        return m;
    }
}
