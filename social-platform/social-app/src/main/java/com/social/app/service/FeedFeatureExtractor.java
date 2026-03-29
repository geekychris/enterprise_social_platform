package com.social.app.service;

import com.social.app.persistence.entity.PostEntity;
import com.social.app.persistence.repository.CommentRepository;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.persistence.repository.PostRepository;
import com.social.app.persistence.repository.ReactionRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.app.service.AnalyticsService.FeedFeatures;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class FeedFeatureExtractor {

    private final ReactionRepository reactionRepo;
    private final CommentRepository commentRepo;
    private final FollowRepository followRepo;
    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final JdbcTemplate jdbcTemplate;

    public FeedFeatureExtractor(ReactionRepository reactionRepo, CommentRepository commentRepo,
            FollowRepository followRepo, PostRepository postRepo, UserRepository userRepo,
            JdbcTemplate jdbcTemplate) {
        this.reactionRepo = reactionRepo;
        this.commentRepo = commentRepo;
        this.followRepo = followRepo;
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Extract features for a single post relative to a viewing user.
     */
    public FeedFeatures extractFeatures(PostEntity post, long viewerUserId,
            Set<Long> followedUserIds, Map<Long, Integer> authorAffinityMap) {

        FeedFeatures f = new FeedFeatures();

        // Engagement: reactions + comments (weighted)
        long reactions = reactionRepo.countByTargetId(post.getId());
        long comments = commentRepo.countByPostId(post.getId());
        f.engagement = reactions * 1.0 + comments * 2.0;
        f.reactionCount = (int) reactions;
        f.commentCount = (int) comments;

        // Recency: hours since posted
        f.recencyHours = Duration.between(post.getCreatedAt(), Instant.now()).toHours() + 0.01;

        // Author affinity: how many of this author's posts the viewer has reacted to
        int affinity = authorAffinityMap.getOrDefault(post.getAuthorId(), 0);
        f.affinity = 1.0 + affinity * 0.3;

        // Author follower count
        f.authorFollowerCount = (int) followRepo.countByFollowedId(post.getAuthorId());

        // Content features: check post_attachments join table
        Integer attachmentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_attachments WHERE post_id = ?",
                Integer.class, post.getId());
        f.hasAttachment = attachmentCount != null && attachmentCount > 0;

        // Check if post has a poll
        Integer pollCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM polls WHERE post_id = ?",
                Integer.class, post.getId());
        f.hasPoll = pollCount != null && pollCount > 0;

        // Not recommended by default; set by caller (e.g., RecommendationService)
        f.isRecommended = false;

        // Social distance
        if (followedUserIds.contains(post.getAuthorId())) {
            f.socialDistance = 1; // direct follow
        } else {
            f.socialDistance = 3; // unknown (2 = friend-of-friend, set by recommendation service)
        }

        return f;
    }

    /**
     * Compute score from features using a heuristic formula.
     * This is the current ranking formula, to be replaced by a GBDT model later.
     */
    public double computeScore(FeedFeatures f) {
        double engagement = f.engagement != null ? f.engagement : 0.0;
        double recencyHours = f.recencyHours != null ? f.recencyHours : 0.01;
        double affinity = f.affinity != null ? f.affinity : 1.0;

        double recencyDecay = Math.pow(0.5, recencyHours / 24.0);
        return engagement * recencyDecay * affinity;
    }

    /**
     * Convert features to a float array for GBDT model input.
     */
    public float[] toFeatureVector(FeedFeatures f) {
        return new float[] {
            f.engagement != null ? f.engagement.floatValue() : 0f,
            f.recencyHours != null ? f.recencyHours.floatValue() : 0f,
            f.affinity != null ? f.affinity.floatValue() : 0f,
            f.reactionCount != null ? f.reactionCount.floatValue() : 0f,
            f.commentCount != null ? f.commentCount.floatValue() : 0f,
            f.authorFollowerCount != null ? f.authorFollowerCount.floatValue() : 0f,
            f.isRecommended != null && f.isRecommended ? 1f : 0f,
            f.hasAttachment != null && f.hasAttachment ? 1f : 0f,
            f.hasPoll != null && f.hasPoll ? 1f : 0f,
            f.socialDistance != null ? f.socialDistance.floatValue() : 3f,
        };
    }
}
