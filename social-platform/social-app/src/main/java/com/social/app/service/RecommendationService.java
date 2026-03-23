package com.social.app.service;

import com.social.app.graph.AoeeGraphClient;
import com.social.app.persistence.entity.PostEntity;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.persistence.repository.MembershipRepository;
import com.social.app.persistence.repository.PostRepository;
import com.social.app.persistence.repository.ReactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Recommendation engine for the social feed. Uses multiple signals to surface
 * content a user is likely to find interesting:
 *
 * 1. Trending posts: high engagement within a recency window
 * 2. Friend-of-friend content: posts from 2nd-degree connections
 * 3. Team-adjacent content: popular posts from teams the user doesn't belong to but
 *    has colleagues in (cross-pollination)
 * 4. Engagement affinity: if a user frequently reacts to a particular author's content,
 *    surface more from that author
 *
 * Scoring: score = engagement_score * recency_decay * affinity_boost
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final double RECENCY_HALF_LIFE_HOURS = 24.0;
    private static final int MAX_CANDIDATE_POSTS = 200;
    private static final int MAX_FOF_AUTHORS = 50;

    private final PostRepository postRepository;
    private final ReactionRepository reactionRepository;
    private final FollowRepository followRepository;
    private final MembershipRepository membershipRepository;
    private final AoeeGraphClient aoeeGraphClient;

    public RecommendationService(PostRepository postRepository,
                                  ReactionRepository reactionRepository,
                                  FollowRepository followRepository,
                                  MembershipRepository membershipRepository,
                                  AoeeGraphClient aoeeGraphClient) {
        this.postRepository = postRepository;
        this.reactionRepository = reactionRepository;
        this.followRepository = followRepository;
        this.membershipRepository = membershipRepository;
        this.aoeeGraphClient = aoeeGraphClient;
    }

    /**
     * Get recommended posts for a user, excluding posts they've already seen in their
     * regular feed (from followed users / joined groups).
     */
    public List<ScoredPost> getRecommendations(long userId, Set<Long> excludePostIds, int limit) {
        Set<Long> followedUserIds = followRepository.findByFollowerId(userId).stream()
                .map(f -> f.getFollowedId())
                .collect(Collectors.toSet());

        Set<Long> memberGroupIds = membershipRepository.findByUserId(userId).stream()
                .map(m -> m.getGroupId())
                .collect(Collectors.toSet());

        // Gather user's reaction history for affinity scoring
        Map<Long, Integer> authorAffinityMap = buildAuthorAffinity(userId);

        // Collect candidate posts from multiple sources
        List<PostEntity> candidates = new ArrayList<>();

        // Source 1: Trending posts (high engagement, recent, public)
        candidates.addAll(getTrendingPosts(excludePostIds));

        // Source 2: Friend-of-friend content via AOEE
        candidates.addAll(getFofPosts(userId, followedUserIds, excludePostIds));

        // Source 3: Cross-team popular content
        candidates.addAll(getCrossTeamPosts(userId, memberGroupIds, excludePostIds));

        // Deduplicate and filter
        Map<Long, PostEntity> uniqueCandidates = new LinkedHashMap<>();
        for (PostEntity post : candidates) {
            if (!excludePostIds.contains(post.getId())
                    && !followedUserIds.contains(post.getAuthorId())
                    && post.getAuthorId() != userId
                    && "PUBLIC".equals(post.getVisibility())) {
                uniqueCandidates.putIfAbsent(post.getId(), post);
            }
        }

        // Score and rank
        List<ScoredPost> scored = uniqueCandidates.values().stream()
                .map(post -> scorePost(post, authorAffinityMap))
                .sorted(Comparator.comparingDouble(ScoredPost::score).reversed())
                .limit(limit)
                .toList();

        log.debug("Generated {} recommendations for user {} from {} candidates",
                scored.size(), userId, uniqueCandidates.size());

        return scored;
    }

    private ScoredPost scorePost(PostEntity post, Map<Long, Integer> authorAffinityMap) {
        // Engagement score: weighted sum of reactions and comments
        long reactionCount = reactionRepository.countByTargetId(post.getId());
        long commentCount = 0; // lightweight: skip comment count for scoring
        double engagementScore = reactionCount * 1.0 + commentCount * 2.0;

        // Recency decay: exponential decay with half-life
        double hoursOld = ChronoUnit.HOURS.between(post.getCreatedAt(), Instant.now());
        double recencyDecay = Math.pow(0.5, hoursOld / RECENCY_HALF_LIFE_HOURS);

        // Affinity boost: if user has reacted to this author before
        int affinityCount = authorAffinityMap.getOrDefault(post.getAuthorId(), 0);
        double affinityBoost = 1.0 + (affinityCount * 0.3); // +30% per prior interaction

        double score = engagementScore * recencyDecay * affinityBoost;

        // Minimum score floor so even new posts with no engagement can appear
        score = Math.max(score, recencyDecay * 0.1);

        return new ScoredPost(post, score);
    }

    /**
     * Build a map of author_id -> count of reactions the user has given to that author's posts.
     * This represents the user's implicit interest in content from specific authors.
     */
    private Map<Long, Integer> buildAuthorAffinity(long userId) {
        try {
            // Get posts the user has reacted to via their reaction history
            List<Long> likedPostIds = aoeeGraphClient.getNeighbors(userId, "LIKES");
            if (likedPostIds.isEmpty()) return Map.of();

            // Cap to avoid huge queries
            List<Long> capped = likedPostIds.size() > 100
                    ? likedPostIds.subList(0, 100) : likedPostIds;

            List<PostEntity> likedPosts = postRepository.findByIdInOrderByCreatedAtDesc(capped);
            Map<Long, Integer> affinityMap = new HashMap<>();
            for (PostEntity post : likedPosts) {
                affinityMap.merge(post.getAuthorId(), 1, Integer::sum);
            }
            return affinityMap;
        } catch (Exception e) {
            log.debug("Could not build affinity map: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Get trending public posts: recent posts with high engagement.
     */
    private List<PostEntity> getTrendingPosts(Set<Long> excludePostIds) {
        try {
            Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
            List<PostEntity> recent = postRepository.findRecentPublicPosts(since, MAX_CANDIDATE_POSTS);
            return recent;
        } catch (Exception e) {
            log.debug("Could not fetch trending posts: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get posts from 2nd-degree connections (friend-of-friend) via AOEE graph traversal.
     */
    private List<PostEntity> getFofPosts(long userId, Set<Long> followedUserIds, Set<Long> excludePostIds) {
        try {
            // Get friends-of-friends from AOEE
            Set<Long> fofUserIds = new HashSet<>();
            for (Long followedId : followedUserIds) {
                if (fofUserIds.size() >= MAX_FOF_AUTHORS) break;
                List<Long> theirFollowed = aoeeGraphClient.getNeighbors(followedId, "FOLLOWS");
                for (Long fofId : theirFollowed) {
                    if (!followedUserIds.contains(fofId) && fofId != userId) {
                        fofUserIds.add(fofId);
                        if (fofUserIds.size() >= MAX_FOF_AUTHORS) break;
                    }
                }
            }

            if (fofUserIds.isEmpty()) return List.of();

            return postRepository.findByAuthorIdInOrderByCreatedAtDesc(new ArrayList<>(fofUserIds))
                    .stream()
                    .limit(MAX_CANDIDATE_POSTS)
                    .toList();
        } catch (Exception e) {
            log.debug("Could not fetch FoF posts: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get popular posts from teams the user doesn't belong to but has colleagues in.
     * This surfaces cross-team content that may be relevant.
     */
    private List<PostEntity> getCrossTeamPosts(long userId, Set<Long> memberGroupIds, Set<Long> excludePostIds) {
        try {
            // Find teams that the user's followed users belong to
            Set<Long> colleagueTeamIds = new HashSet<>();
            List<Long> followedIds = followRepository.findByFollowerId(userId).stream()
                    .map(f -> f.getFollowedId())
                    .limit(20) // cap to avoid excessive queries
                    .toList();

            for (Long followedId : followedIds) {
                membershipRepository.findByUserId(followedId).stream()
                        .map(m -> m.getGroupId())
                        .filter(gid -> !memberGroupIds.contains(gid))
                        .forEach(colleagueTeamIds::add);
            }

            if (colleagueTeamIds.isEmpty()) return List.of();

            // Get recent posts from those teams
            return postRepository.findByTargetIdInOrderByCreatedAtDesc(new ArrayList<>(colleagueTeamIds))
                    .stream()
                    .filter(p -> "PUBLIC".equals(p.getVisibility()) || "TEAM_VISIBLE".equals(p.getVisibility()))
                    .limit(MAX_CANDIDATE_POSTS / 2)
                    .toList();
        } catch (Exception e) {
            log.debug("Could not fetch cross-team posts: {}", e.getMessage());
            return List.of();
        }
    }

    public record ScoredPost(PostEntity post, double score) {}
}
