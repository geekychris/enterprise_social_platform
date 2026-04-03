package com.aisupport.controller;

import com.aisupport.search.UnifiedSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final UnifiedSearchService unifiedSearch;

    public SearchController(UnifiedSearchService unifiedSearch) {
        this.unifiedSearch = unifiedSearch;
    }

    @GetMapping("/lexical/{ksId}")
    public ResponseEntity<List<Map<String, Object>>> lexicalSearch(
            @PathVariable long ksId,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        return ResponseEntity.ok(unifiedSearch.searchLexical(ksId, q, topK));
    }

    @GetMapping("/semantic/{ksId}")
    public ResponseEntity<List<Map<String, Object>>> semanticSearch(
            @PathVariable long ksId,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        return ResponseEntity.ok(unifiedSearch.searchSemantic(ksId, q, topK));
    }

    @GetMapping("/hybrid/{ksId}")
    public ResponseEntity<Map<String, Object>> hybridSearch(
            @PathVariable long ksId,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        var results = unifiedSearch.searchHybrid(ksId, q, topK);
        return ResponseEntity.ok(Map.of(
                "results", results,
                "knowledgeSetId", ksId,
                "query", q
        ));
    }

    @GetMapping("/route")
    public ResponseEntity<List<Map<String, Object>>> routeSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        return ResponseEntity.ok(unifiedSearch.searchAllSemantic(q, topK));
    }
}
