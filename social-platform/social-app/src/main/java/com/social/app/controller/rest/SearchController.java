package com.social.app.controller.rest;

import com.social.app.search.OpenSearchService;
import com.social.app.service.AnalyticsService;
import com.social.core.dto.SearchResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final OpenSearchService openSearchService;
    private final AnalyticsService analyticsService;

    public SearchController(OpenSearchService openSearchService, AnalyticsService analyticsService) {
        this.openSearchService = openSearchService;
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public ResponseEntity<SearchResultDto> search(@RequestParam("q") String query,
                                                  @RequestParam(value = "type", required = false) String type,
                                                  Authentication auth) {
        SearchResultDto result = openSearchService.search(query, type);

        // Log analytics (fire-and-forget)
        try {
            long userId = (Long) auth.getPrincipal();
            analyticsService.logSearch(userId, query, type, (int) result.totalHits());
        } catch (Exception e) {
            log.debug("Failed to log search analytics: {}", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}
