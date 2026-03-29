package com.social.app.service;

import com.social.app.persistence.entity.PostEntity;
import com.social.app.persistence.repository.CommentRepository;
import com.social.app.persistence.repository.FollowRepository;
import com.social.app.persistence.repository.PostRepository;
import com.social.app.persistence.repository.ReactionRepository;
import com.social.app.persistence.repository.UserRepository;
import com.social.app.service.AnalyticsService.FeedFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FeedFeatureExtractorTest {

    @Autowired
    private FeedFeatureExtractor extractor;

    @Autowired
    private ReactionRepository reactionRepo;

    @Autowired
    private CommentRepository commentRepo;

    @Autowired
    private FollowRepository followRepo;

    @Autowired
    private PostRepository postRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PostEntity testPost;
    private long viewerUserId = 999_000L;

    @BeforeEach
    void setUp() {
        // Ensure a user exists for the author
        jdbcTemplate.update(
            "INSERT INTO users (id, username, display_name, email, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
            900_001L, "feat_test_author", "Feature Test Author", "feat_author@test.com"
        );

        // Create a test post
        long postId = System.nanoTime() % 1_000_000_000L;
        jdbcTemplate.update(
            "INSERT INTO posts (id, author_id, content, visibility, created_at, updated_at) " +
            "VALUES (?, ?, ?, 'PUBLIC', ?, NOW())",
            postId, 900_001L, "Test post for feature extraction",
            java.sql.Timestamp.from(Instant.now().minus(6, ChronoUnit.HOURS))
        );

        testPost = postRepo.findById(postId).orElseThrow();
    }

    @Test
    void extractFeatures_computesEngagementFromReactionsAndComments() {
        Set<Long> followedIds = Set.of(testPost.getAuthorId());
        Map<Long, Integer> affinityMap = new HashMap<>();

        FeedFeatures features = extractor.extractFeatures(testPost, viewerUserId, followedIds, affinityMap);

        assertNotNull(features);
        assertNotNull(features.engagement);
        assertTrue(features.engagement >= 0, "Engagement should be non-negative");
        assertNotNull(features.reactionCount);
        assertNotNull(features.commentCount);
        assertEquals(features.reactionCount * 1.0 + features.commentCount * 2.0, features.engagement, 0.001);
    }

    @Test
    void extractFeatures_computesRecencyHours() {
        Set<Long> followedIds = Set.of(testPost.getAuthorId());
        Map<Long, Integer> affinityMap = new HashMap<>();

        FeedFeatures features = extractor.extractFeatures(testPost, viewerUserId, followedIds, affinityMap);

        assertNotNull(features.recencyHours);
        assertTrue(features.recencyHours > 0, "Recency hours should be positive");
        // Post was created ~6 hours ago, so recency should be roughly in that range
        assertTrue(features.recencyHours >= 5.0, "Should be at least 5 hours old");
        assertTrue(features.recencyHours < 8.0, "Should be less than 8 hours old");
    }

    @Test
    void extractFeatures_setsAffinityFromMap() {
        Set<Long> followedIds = Set.of(testPost.getAuthorId());
        Map<Long, Integer> affinityMap = Map.of(testPost.getAuthorId(), 5);

        FeedFeatures features = extractor.extractFeatures(testPost, viewerUserId, followedIds, affinityMap);

        assertNotNull(features.affinity);
        // affinity = 1.0 + 5 * 0.3 = 2.5
        assertEquals(2.5, features.affinity, 0.001);
    }

    @Test
    void extractFeatures_setsSocialDistanceForFollowed() {
        Set<Long> followedIds = Set.of(testPost.getAuthorId());
        Map<Long, Integer> affinityMap = new HashMap<>();

        FeedFeatures features = extractor.extractFeatures(testPost, viewerUserId, followedIds, affinityMap);

        assertEquals(1, features.socialDistance, "Direct follow should have social distance 1");
    }

    @Test
    void extractFeatures_setsSocialDistanceForUnknown() {
        Set<Long> followedIds = Set.of(); // not following the author
        Map<Long, Integer> affinityMap = new HashMap<>();

        FeedFeatures features = extractor.extractFeatures(testPost, viewerUserId, followedIds, affinityMap);

        assertEquals(3, features.socialDistance, "Unknown author should have social distance 3");
    }

    @Test
    void extractFeatures_checksContentFeatures() {
        Set<Long> followedIds = Set.of(testPost.getAuthorId());
        Map<Long, Integer> affinityMap = new HashMap<>();

        FeedFeatures features = extractor.extractFeatures(testPost, viewerUserId, followedIds, affinityMap);

        assertNotNull(features.hasAttachment);
        assertNotNull(features.hasPoll);
        assertFalse(features.isRecommended, "Default isRecommended should be false");
    }

    @Test
    void computeScore_producesPositiveValueForEngagedPost() {
        FeedFeatures f = new FeedFeatures();
        f.engagement = 10.0;
        f.recencyHours = 6.0;
        f.affinity = 2.0;

        double score = extractor.computeScore(f);

        assertTrue(score > 0, "Score should be positive for engaged post");
        // recencyDecay = 0.5^(6/24) = 0.5^0.25 ~ 0.84
        // expected ~ 10 * 0.84 * 2 = 16.8
        assertTrue(score > 15.0, "Score should reflect high engagement and affinity");
        assertTrue(score < 20.0, "Score should be damped by recency decay");
    }

    @Test
    void computeScore_decaysWithOlderPosts() {
        FeedFeatures recent = new FeedFeatures();
        recent.engagement = 5.0;
        recent.recencyHours = 1.0;
        recent.affinity = 1.0;

        FeedFeatures old = new FeedFeatures();
        old.engagement = 5.0;
        old.recencyHours = 48.0;
        old.affinity = 1.0;

        double recentScore = extractor.computeScore(recent);
        double oldScore = extractor.computeScore(old);

        assertTrue(recentScore > oldScore, "Recent posts should score higher than old ones");
    }

    @Test
    void computeScore_handlesNullFeatures() {
        FeedFeatures f = new FeedFeatures();
        // All fields null

        double score = extractor.computeScore(f);

        assertEquals(0.0, score, 0.001, "Score should be 0 when engagement is null/zero");
    }

    @Test
    void toFeatureVector_hasCorrectLength() {
        FeedFeatures f = new FeedFeatures();
        f.engagement = 5.0;
        f.recencyHours = 2.0;
        f.affinity = 1.5;
        f.reactionCount = 3;
        f.commentCount = 1;
        f.authorFollowerCount = 100;
        f.isRecommended = true;
        f.hasAttachment = false;
        f.hasPoll = true;
        f.socialDistance = 2;

        float[] vector = extractor.toFeatureVector(f);

        assertEquals(10, vector.length, "Feature vector should have 10 elements");
    }

    @Test
    void toFeatureVector_mapsValuesCorrectly() {
        FeedFeatures f = new FeedFeatures();
        f.engagement = 5.0;
        f.recencyHours = 2.0;
        f.affinity = 1.5;
        f.reactionCount = 3;
        f.commentCount = 1;
        f.authorFollowerCount = 100;
        f.isRecommended = true;
        f.hasAttachment = false;
        f.hasPoll = true;
        f.socialDistance = 2;

        float[] vector = extractor.toFeatureVector(f);

        assertEquals(5.0f, vector[0], 0.001f, "engagement");
        assertEquals(2.0f, vector[1], 0.001f, "recencyHours");
        assertEquals(1.5f, vector[2], 0.001f, "affinity");
        assertEquals(3.0f, vector[3], 0.001f, "reactionCount");
        assertEquals(1.0f, vector[4], 0.001f, "commentCount");
        assertEquals(100.0f, vector[5], 0.001f, "authorFollowerCount");
        assertEquals(1.0f, vector[6], 0.001f, "isRecommended=true -> 1");
        assertEquals(0.0f, vector[7], 0.001f, "hasAttachment=false -> 0");
        assertEquals(1.0f, vector[8], 0.001f, "hasPoll=true -> 1");
        assertEquals(2.0f, vector[9], 0.001f, "socialDistance");
    }

    @Test
    void toFeatureVector_handlesNullsWithDefaults() {
        FeedFeatures f = new FeedFeatures();
        // All fields null

        float[] vector = extractor.toFeatureVector(f);

        assertEquals(0f, vector[0], "null engagement -> 0");
        assertEquals(0f, vector[1], "null recencyHours -> 0");
        assertEquals(0f, vector[2], "null affinity -> 0");
        assertEquals(0f, vector[3], "null reactionCount -> 0");
        assertEquals(0f, vector[4], "null commentCount -> 0");
        assertEquals(0f, vector[5], "null authorFollowerCount -> 0");
        assertEquals(0f, vector[6], "null isRecommended -> 0");
        assertEquals(0f, vector[7], "null hasAttachment -> 0");
        assertEquals(0f, vector[8], "null hasPoll -> 0");
        assertEquals(3f, vector[9], "null socialDistance -> 3 (default)");
    }
}
