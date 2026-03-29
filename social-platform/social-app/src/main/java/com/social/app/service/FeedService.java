package com.social.app.service;

import com.social.app.persistence.entity.FollowEntity;
import com.social.app.persistence.entity.MembershipEntity;
import com.social.app.persistence.entity.PageMembershipEntity;
import com.social.app.persistence.entity.PostEntity;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.persistence.repository.MembershipRepository;
import com.social.app.persistence.repository.PageMembershipRepository;
import com.social.app.service.AnalyticsService.FeedFeatures;
import com.social.core.dto.FeedResponse;
import com.social.core.dto.PostDto;
import com.social.core.model.Visibility;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles a user's feed by combining:
 * 1. Organic posts: content from followed users and joined teams/groups (chronological)
 * 2. Recommended posts: content surfaced by the RecommendationService (~20% of feed)
 *
 * Recommended posts are interleaved into the feed at regular intervals
 * (roughly every 5th item) and tagged so the UI can indicate "Suggested for you".
 */
@Service
@Transactional(readOnly = true)
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);
    private static final int RECOMMENDATION_INTERVAL = 5; // insert 1 recommended post every N organic posts
    private static final double RECOMMENDATION_RATIO = 0.2; // 20% of feed is recommended

    private final FollowRepository followRepository;
    private final MembershipRepository membershipRepository;
    private final PageMembershipRepository pageMembershipRepository;
    private final PostService postService;
    private final RecommendationService recommendationService;
    private final AnalyticsService analyticsService;
    private final FeedFeatureExtractor feedFeatureExtractor;
    private final StringRedisTemplate redis;
    private final CacheService cacheService;

    public FeedService(FollowRepository followRepository,
                       MembershipRepository membershipRepository,
                       PageMembershipRepository pageMembershipRepository,
                       PostService postService,
                       RecommendationService recommendationService,
                       AnalyticsService analyticsService,
                       FeedFeatureExtractor feedFeatureExtractor,
                       StringRedisTemplate redis,
                       CacheService cacheService) {
        this.followRepository = followRepository;
        this.membershipRepository = membershipRepository;
        this.pageMembershipRepository = pageMembershipRepository;
        this.postService = postService;
        this.recommendationService = recommendationService;
        this.analyticsService = analyticsService;
        this.feedFeatureExtractor = feedFeatureExtractor;
        this.redis = redis;
        this.cacheService = cacheService;
    }

    public FeedResponse assembleFeed(long userId, Long cursor, int limit) {
        // Try pre-computed feed from Redis first
        Set<String> cachedPostIds = redis.opsForZSet().reverseRange("feed:" + userId, 0, limit + 10);
        if (cachedPostIds != null && !cachedPostIds.isEmpty()) {
            List<Long> postIds = cachedPostIds.stream()
                    .map(Long::parseLong)
                    .toList();

            // Apply cursor filter
            List<Long> filteredIds = postIds;
            if (cursor != null) {
                filteredIds = postIds.stream().filter(id -> id < cursor).toList();
            }

            // Load posts from cache/DB by IDs
            List<PostEntity> cachedPosts = filteredIds.stream()
                    .limit(limit)
                    .map(id -> postService.getById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .toList();

            if (!cachedPosts.isEmpty()) {
                // Apply visibility filter using cached group IDs
                String groupsCacheKey = "user:groups:" + userId;
                List<Long> groupIds = cacheService.getWithType(groupsCacheKey,
                        new TypeReference<List<Long>>() {}, Duration.ofSeconds(60), () -> {
                    List<Long> gids = new ArrayList<>(membershipRepository.findByUserId(userId).stream()
                            .map(MembershipEntity::getGroupId).toList());
                    List<Long> pids = pageMembershipRepository.findByUserId(userId).stream()
                            .filter(pm -> "APPROVED".equals(pm.getStatus()))
                            .map(PageMembershipEntity::getPageId).toList();
                    gids.addAll(pids);
                    return gids;
                });

                List<PostEntity> visiblePosts = cachedPosts.stream()
                        .filter(p -> isVisible(p, userId, groupIds))
                        .toList();

                List<PostDto> feed = visiblePosts.stream()
                        .map(p -> postService.toDto(p, userId))
                        .toList();

                boolean hasMore = filteredIds.size() > limit;
                String nextCursor = visiblePosts.isEmpty()
                        ? null
                        : String.valueOf(visiblePosts.getLast().getId());

                return new FeedResponse(feed, nextCursor, hasMore);
            }
        }

        // Fallback: assemble feed from DB

        // Get followed user IDs (cached)
        String followsCacheKey = "user:follows:" + userId;
        List<Long> followedUserIds = cacheService.getWithType(followsCacheKey,
                new TypeReference<List<Long>>() {}, Duration.ofSeconds(60), () -> {
            return followRepository.findByFollowerId(userId).stream()
                    .map(FollowEntity::getFollowedId)
                    .toList();
        });

        // Include own posts
        List<Long> authorIds = new ArrayList<>(followedUserIds);
        authorIds.add(userId);

        // Get team/group IDs user belongs to (cached)
        String groupsCacheKey = "user:groups:" + userId;
        List<Long> groupIds = cacheService.getWithType(groupsCacheKey,
                new TypeReference<List<Long>>() {}, Duration.ofSeconds(60), () -> {
            List<Long> gids = new ArrayList<>(membershipRepository.findByUserId(userId).stream()
                    .map(MembershipEntity::getGroupId).toList());
            List<Long> pids = pageMembershipRepository.findByUserId(userId).stream()
                    .filter(pm -> "APPROVED".equals(pm.getStatus()))
                    .map(PageMembershipEntity::getPageId).toList();
            gids.addAll(pids);
            return gids;
        });

        List<String> targetTypes = List.of("TEAM_FEED", "GROUP_FEED", "PAGE_FEED", "PROJECT_FEED");

        // Query organic posts
        List<PostEntity> allPosts = postService.getFeedPosts(authorIds, targetTypes, groupIds);

        // Apply cursor-based pagination
        List<PostEntity> filteredPosts = allPosts;
        if (cursor != null) {
            filteredPosts = allPosts.stream()
                    .filter(p -> p.getId() < cursor)
                    .toList();
        }

        // Apply visibility filter
        filteredPosts = filteredPosts.stream()
                .filter(p -> isVisible(p, userId, groupIds))
                .toList();

        // Get organic posts for this page
        int organicLimit = (int) Math.ceil(limit * (1.0 - RECOMMENDATION_RATIO));
        List<PostEntity> organicPosts = filteredPosts.stream()
                .limit(organicLimit)
                .toList();

        // Get recommendations to interleave
        Set<Long> organicPostIds = organicPosts.stream()
                .map(PostEntity::getId)
                .collect(Collectors.toSet());
        int recLimit = limit - organicPosts.size();

        List<RecommendationService.ScoredPost> recommendations = List.of();
        if (recLimit > 0) {
            try {
                recommendations = recommendationService.getRecommendations(
                        userId, organicPostIds, recLimit);
            } catch (Exception e) {
                log.warn("Recommendation engine failed, serving organic-only feed: {}",
                        e.getMessage());
            }
        }

        // Interleave: insert a recommended post every RECOMMENDATION_INTERVAL organic posts
        List<PostDto> finalFeed = interleave(organicPosts, recommendations, userId);

        // Log feed impressions with extracted features (fire-and-forget)
        try {
            Set<Long> followedSet = new HashSet<>(followedUserIds);
            // Build author affinity map: count of viewer's reactions to each author's posts
            Map<Long, Integer> authorAffinityMap = new HashMap<>();
            // Build PostEntity lookup for feature extraction
            Map<Long, PostEntity> postEntityMap = new HashMap<>();
            for (PostEntity p : organicPosts) {
                postEntityMap.put(p.getId(), p);
            }
            for (RecommendationService.ScoredPost sp : recommendations) {
                postEntityMap.put(sp.post().getId(), sp.post());
            }

            for (int i = 0; i < finalFeed.size(); i++) {
                PostDto dto = finalFeed.get(i);
                PostEntity entity = postEntityMap.get(dto.id());
                if (entity == null) continue;

                FeedFeatures features = feedFeatureExtractor.extractFeatures(
                        entity, userId, followedSet, authorAffinityMap);
                if (dto.recommended()) {
                    features.isRecommended = true;
                }
                double score = feedFeatureExtractor.computeScore(features);
                String source = dto.recommended() ? "recommended" : "organic";
                String targetType = dto.targetType() != null ? dto.targetType().name() : null;

                analyticsService.logFeedImpression(
                        userId, dto.id(), dto.author().id(), i, score, source,
                        targetType, dto.targetId(), features);
            }
        } catch (Exception e) {
            log.debug("Failed to log feed impressions: {}", e.getMessage());
        }

        // Determine pagination cursor from the last organic post
        boolean hasMore = filteredPosts.size() > organicLimit;
        String nextCursor = organicPosts.isEmpty()
                ? null
                : String.valueOf(organicPosts.getLast().getId());

        return new FeedResponse(finalFeed, nextCursor, hasMore);
    }

    private List<PostDto> interleave(List<PostEntity> organic,
                                      List<RecommendationService.ScoredPost> recommended,
                                      long userId) {
        List<PostDto> result = new ArrayList<>();
        Iterator<RecommendationService.ScoredPost> recIter = recommended.iterator();
        int organicCount = 0;

        for (PostEntity post : organic) {
            result.add(postService.toDto(post, userId));
            organicCount++;

            // Every N organic posts, insert a recommendation if available
            if (organicCount % RECOMMENDATION_INTERVAL == 0 && recIter.hasNext()) {
                RecommendationService.ScoredPost rec = recIter.next();
                PostDto recDto = postService.toDto(rec.post(), userId);
                // Mark as recommended by wrapping with a modified DTO
                result.add(withRecommendedFlag(recDto, rec.score()));
            }
        }

        // Append any remaining recommendations at the end
        while (recIter.hasNext()) {
            RecommendationService.ScoredPost rec = recIter.next();
            PostDto recDto = postService.toDto(rec.post(), userId);
            result.add(withRecommendedFlag(recDto, rec.score()));
        }

        return result;
    }

    private PostDto withRecommendedFlag(PostDto dto, double score) {
        return dto.asRecommended(score);
    }

    private boolean isVisible(PostEntity post, long viewerId, List<Long> viewerGroupIds) {
        String vis = post.getVisibility();
        if (Visibility.PUBLIC.name().equals(vis)) return true;
        if (post.getAuthorId().equals(viewerId)) return true;
        if (Visibility.PRIVATE.name().equals(vis)) return false;
        if (Visibility.TEAM_VISIBLE.name().equals(vis) || Visibility.RESTRICTED.name().equals(vis)) {
            if (post.getTargetId() != null && viewerGroupIds.contains(post.getTargetId())) {
                return true;
            }
        }
        return false;
    }
}
