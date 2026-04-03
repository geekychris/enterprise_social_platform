package com.aisupport.controller;

import com.aisupport.search.LuceneSearchService;
import com.aisupport.search.VectorSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final LuceneSearchService luceneSearch;
    private final VectorSearchService vectorSearch;

    public SearchController(LuceneSearchService luceneSearch, VectorSearchService vectorSearch) {
        this.luceneSearch = luceneSearch;
        this.vectorSearch = vectorSearch;
    }

    @GetMapping("/lexical/{ksId}")
    public ResponseEntity<List<Map<String, Object>>> lexicalSearch(
            @PathVariable long ksId,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        return ResponseEntity.ok(luceneSearch.search(ksId, q, topK));
    }

    @GetMapping("/semantic/{ksId}")
    public ResponseEntity<List<Map<String, Object>>> semanticSearch(
            @PathVariable long ksId,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        return ResponseEntity.ok(vectorSearch.search(ksId, q, topK));
    }

    @GetMapping("/hybrid/{ksId}")
    public ResponseEntity<Map<String, Object>> hybridSearch(
            @PathVariable long ksId,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        var lexical = luceneSearch.search(ksId, q, topK);
        var semantic = vectorSearch.search(ksId, q, topK);
        return ResponseEntity.ok(Map.of(
                "lexical", lexical,
                "semantic", semantic,
                "knowledgeSetId", ksId,
                "query", q
        ));
    }

    @GetMapping("/route")
    public ResponseEntity<List<Map<String, Object>>> routeSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK) {
        return ResponseEntity.ok(vectorSearch.searchAll(q, topK));
    }
}
