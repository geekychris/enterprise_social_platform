package com.social.app.controller.rest;

import com.social.app.search.OpenSearchService;
import com.social.core.dto.SearchResultDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final OpenSearchService openSearchService;

    public SearchController(OpenSearchService openSearchService) {
        this.openSearchService = openSearchService;
    }

    @GetMapping
    public ResponseEntity<SearchResultDto> search(@RequestParam("q") String query,
                                                  @RequestParam(value = "type", required = false) String type) {
        return ResponseEntity.ok(openSearchService.search(query, type));
    }
}
