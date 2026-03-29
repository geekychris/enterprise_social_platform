package com.social.app.controller.rest;

import com.social.app.persistence.entity.PostEntity;
import com.social.app.persistence.entity.UserEntity;
import com.social.app.persistence.repository.PostRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.app.service.AnalyticsService;
import com.social.app.service.FeedFeatureExtractor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ML Playground API — allows admins to explore feed ranking features,
 * view analytics data, and experiment with scoring.
 */
@RestController
@RequestMapping("/api/admin/ml")
public class MLPlaygroundController {

    private final FeedFeatureExtractor featureExtractor;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;

    public MLPlaygroundController(FeedFeatureExtractor featureExtractor,
                                   PostRepository postRepository,
                                   UserRepository userRepository,
                                   JdbcTemplate jdbc) {
        this.featureExtractor = featureExtractor;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.jdbc = jdbc;
    }

    /**
     * Extract and score features for recent posts for a given user.
     * Shows what the ranking model sees.
     */
    @GetMapping("/features")
    public ResponseEntity<Map<String, Object>> getFeatures(
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        long userId = (Long) auth.getPrincipal();

        // Get recent posts
        var posts = postRepository.findAll().stream()
                .sorted(Comparator.comparing(PostEntity::getCreatedAt).reversed())
                .limit(limit)
                .toList();

        // Get user's follows for social distance
        Set<Long> followedIds = new HashSet<>();
        try {
            var follows = jdbc.queryForList(
                    "SELECT followed_id FROM follows WHERE follower_id = ?", Long.class, userId);
            followedIds.addAll(follows);
        } catch (Exception ignored) {}

        // Extract features for each post
        List<Map<String, Object>> results = new ArrayList<>();
        for (PostEntity post : posts) {
            var features = featureExtractor.extractFeatures(post, userId, followedIds, Map.of());
            double score = featureExtractor.computeScore(features);
            float[] vector = featureExtractor.toFeatureVector(features);

            String authorName = userRepository.findById(post.getAuthorId())
                    .map(u -> u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                    .orElse("Unknown");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("postId", post.getId());
            row.put("authorName", authorName);
            row.put("content", post.getContent() != null ? post.getContent().substring(0, Math.min(post.getContent().length(), 80)) : "");
            row.put("score", Math.round(score * 10000.0) / 10000.0);
            row.put("features", Map.of(
                    "engagement", features.engagement,
                    "recencyHours", features.recencyHours,
                    "affinity", features.affinity,
                    "reactionCount", features.reactionCount,
                    "commentCount", features.commentCount,
                    "authorFollowers", features.authorFollowerCount,
                    "isRecommended", features.isRecommended,
                    "hasAttachment", features.hasAttachment,
                    "hasPoll", features.hasPoll,
                    "socialDistance", features.socialDistance
            ));
            row.put("featureVector", vector);
            results.add(row);
        }

        // Sort by score descending
        results.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));

        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "postCount", results.size(),
                "posts", results
        ));
    }

    /**
     * Get analytics event counts from Kafka topics.
     */
    @GetMapping("/analytics/stats")
    public ResponseEntity<Map<String, Object>> getAnalyticsStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Feed entries in DB
        try {
            Long feedEntries = jdbc.queryForObject("SELECT COUNT(*) FROM feed_entries", Long.class);
            stats.put("feedEntries", feedEntries);
        } catch (Exception e) { stats.put("feedEntries", "error: " + e.getMessage()); }

        // Unread counts
        try {
            Long unreadRows = jdbc.queryForObject("SELECT COUNT(*) FROM unread_counts", Long.class);
            stats.put("unreadCountRows", unreadRows);
        } catch (Exception e) { stats.put("unreadCountRows", "error"); }

        // Conversation summaries
        try {
            Long summaries = jdbc.queryForObject("SELECT COUNT(*) FROM conversation_summaries", Long.class);
            stats.put("conversationSummaries", summaries);
        } catch (Exception e) { stats.put("conversationSummaries", "error"); }

        // Content counts
        try {
            stats.put("totalPosts", jdbc.queryForObject("SELECT COUNT(*) FROM posts", Long.class));
            stats.put("totalMessages", jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class));
            stats.put("totalReactions", jdbc.queryForObject("SELECT COUNT(*) FROM reactions", Long.class));
            stats.put("totalComments", jdbc.queryForObject("SELECT COUNT(*) FROM comments", Long.class));
            stats.put("totalUsers", jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class));
            stats.put("totalPolls", jdbc.queryForObject("SELECT COUNT(*) FROM polls", Long.class));
            stats.put("totalOrgUnits", jdbc.queryForObject("SELECT COUNT(*) FROM org_units", Long.class));
        } catch (Exception ignored) {}

        return ResponseEntity.ok(stats);
    }

    /**
     * Score a single post with custom user features (for experimentation).
     */
    @PostMapping("/score")
    public ResponseEntity<Map<String, Object>> scorePost(
            @RequestBody Map<String, Object> body) {
        // Allow manual feature input
        AnalyticsService.FeedFeatures features = new AnalyticsService.FeedFeatures();
        features.engagement = getDouble(body, "engagement", 0.0);
        features.recencyHours = getDouble(body, "recencyHours", 1.0);
        features.affinity = getDouble(body, "affinity", 1.0);
        features.reactionCount = getInt(body, "reactionCount", 0);
        features.commentCount = getInt(body, "commentCount", 0);
        features.authorFollowerCount = getInt(body, "authorFollowers", 10);
        features.isRecommended = getBoolean(body, "isRecommended", false);
        features.hasAttachment = getBoolean(body, "hasAttachment", false);
        features.hasPoll = getBoolean(body, "hasPoll", false);
        features.socialDistance = getInt(body, "socialDistance", 3);

        double score = featureExtractor.computeScore(features);
        float[] vector = featureExtractor.toFeatureVector(features);

        return ResponseEntity.ok(Map.of(
                "score", score,
                "featureVector", vector,
                "features", Map.of(
                        "engagement", features.engagement,
                        "recencyHours", features.recencyHours,
                        "affinity", features.affinity,
                        "reactionCount", features.reactionCount,
                        "commentCount", features.commentCount,
                        "authorFollowers", features.authorFollowerCount,
                        "isRecommended", features.isRecommended,
                        "hasAttachment", features.hasAttachment,
                        "hasPoll", features.hasPoll,
                        "socialDistance", features.socialDistance
                )
        ));
    }

    /**
     * Get model training info.
     */
    @GetMapping("/model/info")
    public ResponseEntity<Map<String, Object>> getModelInfo() {
        return ResponseEntity.ok(Map.of(
                "modelType", "heuristic (engagement × recency_decay × affinity)",
                "featureCount", 10,
                "featureNames", List.of(
                        "engagement", "recencyHours", "affinity", "reactionCount",
                        "commentCount", "authorFollowers", "isRecommended",
                        "hasAttachment", "hasPoll", "socialDistance"
                ),
                "userFeatures", List.of("affinity", "socialDistance"),
                "itemFeatures", List.of("engagement", "recencyHours", "reactionCount",
                        "commentCount", "authorFollowers", "isRecommended", "hasAttachment", "hasPoll"),
                "scoringFormula", "engagement × 0.5^(recencyHours/24) × affinity",
                "status", "collecting training data — GBDT model will replace heuristic after sufficient data",
                "kafkaTopics", Map.of(
                        "impressions", "worksphere-feed-impressions",
                        "interactions", "worksphere-user-interactions"
                )
        ));
    }

    private double getDouble(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return def;
    }

    private int getInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    private boolean getBoolean(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        return def;
    }
}
