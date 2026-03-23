package com.social.app.controller.rest;

import com.social.app.graph.AoeeGraphClient;
import com.social.app.service.UserService;
import com.social.core.dto.UserSummaryDto;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.persistence.repository.ReactionRepository;
import com.social.app.persistence.repository.PostRepository;
import com.social.app.persistence.repository.MembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Graph exploration endpoints for the admin UI.
 * Proxies AOEE graph queries and enriches results with user profile data.
 */
@RestController
@RequestMapping("/api/admin/graph")
public class GraphExplorerController {

    private static final Logger log = LoggerFactory.getLogger(GraphExplorerController.class);

    private final AoeeGraphClient aoeeClient;
    private final UserService userService;
    private final FollowRepository followRepository;
    private final ReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final MembershipRepository membershipRepository;

    public GraphExplorerController(AoeeGraphClient aoeeClient,
                                    UserService userService,
                                    FollowRepository followRepository,
                                    ReactionRepository reactionRepository,
                                    PostRepository postRepository,
                                    MembershipRepository membershipRepository) {
        this.aoeeClient = aoeeClient;
        this.userService = userService;
        this.followRepository = followRepository;
        this.reactionRepository = reactionRepository;
        this.postRepository = postRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Get neighbors of a node, enriched with user profile data where applicable.
     */
    @GetMapping("/neighbors/{src}/{edgeType}")
    public ResponseEntity<Map<String, Object>> getNeighbors(
            @PathVariable long src,
            @PathVariable String edgeType) {
        List<Long> neighborIds = aoeeClient.getNeighbors(src, edgeType);
        List<Map<String, Object>> enriched = enrichUserIds(neighborIds);
        return ResponseEntity.ok(Map.of(
                "src", src,
                "edgeType", edgeType,
                "neighbors", enriched,
                "count", neighborIds.size()
        ));
    }

    /**
     * Multi-hop traversal: get neighbors, then neighbors of neighbors.
     */
    @GetMapping("/traverse/{src}/{edgeType}")
    public ResponseEntity<Map<String, Object>> traverse(
            @PathVariable long src,
            @PathVariable String edgeType,
            @RequestParam(defaultValue = "2") int depth,
            @RequestParam(defaultValue = "50") int maxPerHop) {

        // Build adjacency map level by level
        Map<Long, List<Long>> adjacency = new LinkedHashMap<>();
        Set<Long> visited = new HashSet<>();
        List<Long> frontier = List.of(src);
        visited.add(src);

        for (int d = 0; d < depth && !frontier.isEmpty(); d++) {
            List<Long> nextFrontier = new ArrayList<>();
            for (Long nodeId : frontier) {
                List<Long> neighbors = aoeeClient.getNeighbors(nodeId, edgeType);
                List<Long> limited = neighbors.stream().limit(maxPerHop).toList();
                adjacency.put(nodeId, limited);
                for (Long n : limited) {
                    if (visited.add(n)) {
                        nextFrontier.add(n);
                    }
                }
            }
            frontier = nextFrontier;
        }

        // Build nodes with user info
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Long id : visited) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", id);
            node.put("depth", adjacency.containsKey(id) ? 0 : -1); // approximate
            userService.getById(id).ifPresent(user -> {
                node.put("username", user.getUsername());
                node.put("displayName", user.getDisplayName());
                node.put("avatarUrl", user.getAvatarUrl());
            });
            nodes.add(node);
        }

        // Build edges
        List<Map<String, Object>> edges = new ArrayList<>();
        for (var entry : adjacency.entrySet()) {
            for (Long dst : entry.getValue()) {
                edges.add(Map.of("src", entry.getKey(), "dst", dst, "edgeType", edgeType));
            }
        }

        return ResponseEntity.ok(Map.of(
                "nodes", nodes,
                "edges", edges,
                "root", src,
                "depth", depth,
                "edgeType", edgeType
        ));
    }

    /**
     * Friend-of-friend query with user enrichment.
     */
    @PostMapping("/fof")
    public ResponseEntity<Map<String, Object>> friendOfFriend(@RequestBody Map<String, Object> body) {
        long sourceId = parseLong(body.get("sourceId"));
        String edgeType = (String) body.getOrDefault("edgeType", "FOLLOWS");
        int maxResults = ((Number) body.getOrDefault("maxResults", 20)).intValue();
        double minScore = ((Number) body.getOrDefault("minScore", 0.0)).doubleValue();

        Map<String, Object> result = aoeeClient.friendOfFriend(sourceId, edgeType, maxResults, minScore);

        // Enrich candidates with user data
        Object candidatesObj = result.get("candidates");
        if (candidatesObj instanceof List<?> candidates) {
            List<Map<String, Object>> enriched = new ArrayList<>();
            for (Object c : candidates) {
                if (c instanceof Map<?, ?> candidate) {
                    Map<String, Object> enrichedCandidate = new HashMap<>((Map<String, Object>) candidate);
                    Object idObj = candidate.get("id");
                    if (idObj != null) {
                        long id = idObj instanceof Number n ? n.longValue() : Long.parseLong(idObj.toString());
                        userService.getById(id).ifPresent(user -> {
                            enrichedCandidate.put("username", user.getUsername());
                            enrichedCandidate.put("displayName", user.getDisplayName());
                            enrichedCandidate.put("avatarUrl", user.getAvatarUrl());
                        });
                    }
                    enriched.add(enrichedCandidate);
                }
            }
            result = new HashMap<>(result);
            result.put("candidates", enriched);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Mutual friends between two users with user enrichment.
     */
    @PostMapping("/mutual-friends")
    public ResponseEntity<Map<String, Object>> mutualFriends(@RequestBody Map<String, Object> body) {
        long userId1 = parseLong(body.get("userId1"));
        long userId2 = parseLong(body.get("userId2"));
        String edgeType = (String) body.getOrDefault("edgeType", "FOLLOWS");

        Map<String, Object> result = aoeeClient.mutualFriends(userId1, userId2, edgeType);

        // Enrich mutual friend IDs with user data
        Object mutualObj = result.get("mutualFriends");
        if (mutualObj instanceof List<?> mutualIds) {
            List<Map<String, Object>> enriched = enrichIds(mutualIds);
            result = new HashMap<>(result);
            result.put("mutualFriends", enriched);
        }

        // Also enrich with user info for the two input users
        Map<String, Object> enrichedResult = new HashMap<>(result);
        userService.getById(userId1).ifPresent(u -> enrichedResult.put("user1", Map.of(
                "id", u.getId(), "username", u.getUsername(), "displayName", u.getDisplayName())));
        userService.getById(userId2).ifPresent(u -> enrichedResult.put("user2", Map.of(
                "id", u.getId(), "username", u.getUsername(), "displayName", u.getDisplayName())));

        return ResponseEntity.ok(enrichedResult);
    }

    /**
     * Set intersection of two nodes' neighbors.
     */
    @PostMapping("/intersect")
    public ResponseEntity<Map<String, Object>> intersect(@RequestBody Map<String, Object> body) {
        long id1 = parseLong(body.get("id1"));
        long id2 = parseLong(body.get("id2"));
        String edgeType = (String) body.getOrDefault("edgeType", "FOLLOWS");

        Map<String, Object> result = aoeeClient.intersect(id1, id2, edgeType);
        Object idsObj = result.get("ids");
        if (idsObj instanceof List<?> ids) {
            result = new HashMap<>(result);
            result.put("results", enrichIds(ids));
            result.put("count", ids.size());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * AOEE stats and health.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        Map<String, Object> stats = aoeeClient.getStats();
        stats = new HashMap<>(stats);
        stats.put("available", aoeeClient.isAvailable());
        return ResponseEntity.ok(stats);
    }

    /**
     * Get edge counts for a node across all edge types.
     */
    @GetMapping("/profile/{nodeId}")
    public ResponseEntity<Map<String, Object>> nodeProfile(@PathVariable long nodeId) {
        String[] edgeTypes = {"FOLLOWS", "LIKES", "AUTHORED", "MEMBER_OF", "CONTAINS"};
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String type : edgeTypes) {
            long count = aoeeClient.count(nodeId, type);
            if (count > 0) counts.put(type, count);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodeId", nodeId);
        result.put("edgeCounts", counts);

        userService.getById(nodeId).ifPresent(user -> {
            result.put("username", user.getUsername());
            result.put("displayName", user.getDisplayName());
            result.put("avatarUrl", user.getAvatarUrl());
        });

        return ResponseEntity.ok(result);
    }

    /**
     * Backfill AOEE with existing DB data (follows, reactions, posts, memberships).
     * This loads the social graph from PostgreSQL into the AOEE cache.
     */
    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfill() {
        Map<String, Integer> counts = new LinkedHashMap<>();

        // Backfill FOLLOWS edges
        int followCount = 0;
        for (var follow : followRepository.findAll()) {
            aoeeClient.addEdge(follow.getFollowerId(), "FOLLOWS", follow.getFollowedId());
            followCount++;
        }
        counts.put("FOLLOWS", followCount);
        log.info("AOEE backfill: synced {} FOLLOWS edges", followCount);

        // Backfill LIKES edges
        int likeCount = 0;
        for (var reaction : reactionRepository.findAll()) {
            aoeeClient.addEdge(reaction.getUserId(), "LIKES", reaction.getTargetId());
            likeCount++;
        }
        counts.put("LIKES", likeCount);
        log.info("AOEE backfill: synced {} LIKES edges", likeCount);

        // Backfill AUTHORED edges
        int authoredCount = 0;
        for (var post : postRepository.findAll()) {
            aoeeClient.addEdge(post.getAuthorId(), "AUTHORED", post.getId());
            if (post.getTargetId() != null) {
                aoeeClient.addEdge(post.getTargetId(), "CONTAINS", post.getId());
            }
            authoredCount++;
        }
        counts.put("AUTHORED", authoredCount);
        log.info("AOEE backfill: synced {} AUTHORED edges", authoredCount);

        // Backfill MEMBER_OF edges
        int memberCount = 0;
        for (var membership : membershipRepository.findAll()) {
            if ("APPROVED".equals(membership.getStatus())) {
                aoeeClient.addEdge(membership.getUserId(), "MEMBER_OF", membership.getGroupId());
                memberCount++;
            }
        }
        counts.put("MEMBER_OF", memberCount);
        log.info("AOEE backfill: synced {} MEMBER_OF edges", memberCount);

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return ResponseEntity.ok(Map.of(
                "status", "complete",
                "edgesByType", counts,
                "totalEdges", total
        ));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private List<Map<String, Object>> enrichUserIds(List<Long> ids) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : ids) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", id);
            userService.getById(id).ifPresent(user -> {
                item.put("username", user.getUsername());
                item.put("displayName", user.getDisplayName());
                item.put("avatarUrl", user.getAvatarUrl());
            });
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> enrichIds(List<?> ids) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object obj : ids) {
            long id = obj instanceof Number n ? n.longValue() : Long.parseLong(obj.toString());
            Map<String, Object> item = new HashMap<>();
            item.put("id", id);
            userService.getById(id).ifPresent(user -> {
                item.put("username", user.getUsername());
                item.put("displayName", user.getDisplayName());
                item.put("avatarUrl", user.getAvatarUrl());
            });
            result.add(item);
        }
        return result;
    }

    private long parseLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}
