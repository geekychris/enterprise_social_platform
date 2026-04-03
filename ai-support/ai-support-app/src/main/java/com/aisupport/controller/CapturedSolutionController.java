package com.aisupport.controller;

import com.aisupport.persistence.entity.CapturedSolutionEntity;
import com.aisupport.persistence.repository.CapturedSolutionRepository;
import com.aisupport.service.KnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/solutions")
public class CapturedSolutionController {

    private final CapturedSolutionRepository solutionRepository;
    private final KnowledgeService knowledgeService;

    public CapturedSolutionController(CapturedSolutionRepository solutionRepository,
                                       KnowledgeService knowledgeService) {
        this.solutionRepository = solutionRepository;
        this.knowledgeService = knowledgeService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "PENDING") String status) {
        var solutions = solutionRepository.findByStatusOrderByCreatedAtDesc(status);
        return ResponseEntity.ok(solutions.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<Map<String, Object>> promote(@PathVariable long id,
                                                        @RequestBody Map<String, Object> body) {
        var solution = solutionRepository.findById(id).orElse(null);
        if (solution == null) return ResponseEntity.notFound().build();

        String tier = (String) body.getOrDefault("tier", "FIRST_CLASS");
        String title = (String) body.getOrDefault("title", "Community Solution: " + solution.getQuestion());

        // Add to knowledge set as a document
        String content = solution.getSolution();
        if (!"FIRST_CLASS".equals(tier)) {
            content = "[Community Solution by " + solution.getSourceUsername() + "]\n\n" + content;
        }

        var doc = knowledgeService.addDocument(
                solution.getKnowledgeSetId(),
                title,
                content,
                null,
                "FIRST_CLASS".equals(tier) ? "CURATED" : "COMMUNITY"
        );
        knowledgeService.indexDocument(doc.getId());

        solution.setStatus("PROMOTED");
        solution.setPromotedToDocumentId(doc.getId());
        solution.setReviewerNotes((String) body.get("notes"));
        solution.setReviewedAt(OffsetDateTime.now());
        solutionRepository.save(solution);

        return ResponseEntity.ok(Map.of("status", "promoted", "documentId", doc.getId()));
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Map<String, String>> dismiss(@PathVariable long id,
                                                        @RequestBody Map<String, Object> body) {
        var solution = solutionRepository.findById(id).orElse(null);
        if (solution == null) return ResponseEntity.notFound().build();

        solution.setStatus("DISMISSED");
        solution.setReviewerNotes((String) body.get("notes"));
        solution.setReviewedAt(OffsetDateTime.now());
        solutionRepository.save(solution);

        return ResponseEntity.ok(Map.of("status", "dismissed"));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "pending", solutionRepository.countByStatus("PENDING"),
                "promoted", solutionRepository.countByStatus("PROMOTED"),
                "dismissed", solutionRepository.countByStatus("DISMISSED")
        ));
    }

    private Map<String, Object> toDto(CapturedSolutionEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("knowledgeSetId", s.getKnowledgeSetId());
        m.put("question", s.getQuestion());
        m.put("solution", s.getSolution());
        m.put("sourceUsername", s.getSourceUsername());
        m.put("sourceType", s.getSourceType());
        m.put("status", s.getStatus());
        m.put("createdAt", s.getCreatedAt());
        return m;
    }
}
