package com.aisupport.controller;

import com.aisupport.service.OllamaService;
import com.aisupport.service.KnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final OllamaService ollamaService;
    private final KnowledgeService knowledgeService;

    public HealthController(OllamaService ollamaService, KnowledgeService knowledgeService) {
        this.ollamaService = ollamaService;
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        status.put("ollamaAvailable", ollamaService.isAvailable());
        status.put("knowledgeSets", knowledgeService.getAllKnowledgeSets().size());
        return ResponseEntity.ok(status);
    }
}
