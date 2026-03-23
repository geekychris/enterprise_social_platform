package com.social.app.controller.rest;

import com.social.app.service.FeedService;
import com.social.app.service.RecommendationService;
import com.social.core.dto.FeedResponse;
import com.social.core.dto.PostDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedService feedService;
    private final RecommendationService recommendationService;
    private final com.social.app.service.PostService postService;

    public FeedController(FeedService feedService,
                          RecommendationService recommendationService,
                          com.social.app.service.PostService postService) {
        this.feedService = feedService;
        this.recommendationService = recommendationService;
        this.postService = postService;
    }

    @GetMapping
    public ResponseEntity<FeedResponse> getFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        Long cursorLong = cursor != null ? Long.parseLong(cursor) : null;
        return ResponseEntity.ok(feedService.assembleFeed(userId, cursorLong, limit));
    }

    @GetMapping("/recommended")
    public ResponseEntity<List<PostDto>> getRecommended(
            @RequestParam(defaultValue = "10") int limit,
            Authentication auth) {
        long userId = (Long) auth.getPrincipal();
        List<RecommendationService.ScoredPost> recs =
                recommendationService.getRecommendations(userId, Set.of(), limit);
        List<PostDto> posts = recs.stream()
                .map(r -> postService.toDto(r.post(), userId).asRecommended(r.score()))
                .toList();
        return ResponseEntity.ok(posts);
    }
}
