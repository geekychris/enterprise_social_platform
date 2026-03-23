package com.social.datagen.generator;

import com.social.core.id.GlobalId;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates comments on posts with power-law distribution (some posts get many
 * comments, most get few). Creates top-level comments and replies (depth max 1).
 */
public class CommentGenerator {

    private static final Logger log = LoggerFactory.getLogger(CommentGenerator.class);

    private static final String INSERT_COMMENT_SQL =
            "INSERT INTO comments (id, post_id, parent_comment_id, author_id, content, " +
            "depth, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

    /** Edges for AOEE sync */
    public record CommentEdge(long commentId, long postId, long authorId) {}

    private static final String[] COMMENT_TEMPLATES = {
            "Great update! Thanks for sharing.",
            "This is really helpful, thanks!",
            "+1, love this approach.",
            "Can you share more details about the timeline?",
            "Awesome work! The team should be proud.",
            "I have a question about %s - can we discuss offline?",
            "Agreed. We should also consider %s.",
            "Nice! How does this compare to what we did last quarter?",
            "Thanks for the heads up. I'll update my team.",
            "This aligns well with what we're doing in %s.",
            "Strong +1. Let me know how I can help.",
            "Interesting perspective. Have you considered %s?",
            "Thanks for raising this. It's been on my mind too.",
            "Congratulations! Well deserved.",
            "Following this thread. Very relevant to our work.",
            "Could we schedule a follow-up meeting on this?",
            "Great question! In my experience, %s works best.",
            "Love the progress here. Keep it up!",
    };

    private static final String[] REPLY_TEMPLATES = {
            "Good point! I agree.",
            "Thanks for the feedback.",
            "I'll look into that and follow up.",
            "That's a great suggestion, thanks!",
            "Noted. Will incorporate this into the plan.",
            "Interesting - hadn't thought about it that way.",
            "Let me check with the team and get back to you.",
            "+1 to this.",
            "Makes sense. Let's sync on this.",
            "Appreciate the input!",
    };

    private static final String[] TOPICS = {
            "scalability", "testing", "documentation", "monitoring",
            "automation", "user experience", "performance", "security",
            "reliability", "accessibility", "onboarding", "tooling"
    };

    private final JdbcTemplate jdbc;
    private final GlobalIdGenerator idGen;
    private final Faker faker;
    private final Random random;
    private final int batchSize;

    public CommentGenerator(JdbcTemplate jdbc, GlobalIdGenerator idGen, Faker faker,
                            Random random, int batchSize) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.faker = faker;
        this.random = random;
        this.batchSize = batchSize;
    }

    /**
     * Generate comments with power-law distribution across posts.
     * ~30% of top-level comments get a reply (depth max 1).
     */
    public List<CommentEdge> generate(int count, List<GlobalId> postIds, List<GlobalId> userIds) {
        log.info("Generating {} comments...", count);
        List<CommentEdge> edges = new ArrayList<>();

        // Assign comment counts to posts using power law
        // More recent posts (later in list) tend to have more comments
        int postCount = postIds.size();
        double[] postWeights = new double[postCount];
        for (int i = 0; i < postCount; i++) {
            // Zipf-like: weight = 1 / rank^0.8, with some randomness
            int rank = postCount - i; // later posts are "more recent"
            postWeights[i] = 1.0 / Math.pow(rank, 0.8) + random.nextDouble() * 0.1;
        }

        // Normalize weights
        double totalWeight = 0;
        for (double w : postWeights) totalWeight += w;
        for (int i = 0; i < postCount; i++) {
            postWeights[i] /= totalWeight;
        }

        // Phase 1: Generate top-level comments (~70% of total)
        int topLevelCount = (int) (count * 0.7);
        int replyCount = count - topLevelCount;

        // Track top-level comment IDs per post for reply generation
        List<GlobalId> topLevelCommentIds = new ArrayList<>();
        List<Long> topLevelPostIds = new ArrayList<>();

        jdbc.execute(INSERT_COMMENT_SQL, (PreparedStatement ps) -> {
            int batchCount = 0;
            Instant now = Instant.now();

            // Top-level comments
            for (int i = 0; i < topLevelCount; i++) {
                GlobalId commentId = idGen.next(ObjectType.COMMENT);
                int postIdx = pickWeighted(postWeights);
                GlobalId postId = postIds.get(postIdx);
                GlobalId authorId = userIds.get(random.nextInt(userIds.size()));

                topLevelCommentIds.add(commentId);
                topLevelPostIds.add(postId.value());
                edges.add(new CommentEdge(commentId.value(), postId.value(), authorId.value()));

                String content = generateCommentContent();
                Timestamp createdAt = Timestamp.from(
                        now.minus((long) (random.nextDouble() * 4000), ChronoUnit.HOURS));

                ps.setLong(1, commentId.value());
                ps.setLong(2, postId.value());
                ps.setNull(3, java.sql.Types.BIGINT); // no parent
                ps.setLong(4, authorId.value());
                ps.setString(5, content);
                ps.setShort(6, (short) 0); // depth = 0 for top-level
                ps.setTimestamp(7, createdAt);
                ps.addBatch();
                batchCount++;

                if (batchCount >= batchSize) {
                    ps.executeBatch();
                    batchCount = 0;
                }

                if ((i + 1) % 1000 == 0) {
                    log.info("  Top-level comments generated: {}/{}", i + 1, topLevelCount);
                }
            }

            // Replies to ~30% of top-level comments (depth = 1)
            for (int i = 0; i < replyCount; i++) {
                GlobalId commentId = idGen.next(ObjectType.COMMENT);
                int parentIdx = random.nextInt(topLevelCommentIds.size());
                GlobalId parentCommentId = topLevelCommentIds.get(parentIdx);
                long postIdValue = topLevelPostIds.get(parentIdx);
                GlobalId authorId = userIds.get(random.nextInt(userIds.size()));

                edges.add(new CommentEdge(commentId.value(), postIdValue, authorId.value()));

                String content = generateReplyContent();
                Timestamp createdAt = Timestamp.from(
                        now.minus((long) (random.nextDouble() * 3500), ChronoUnit.HOURS));

                ps.setLong(1, commentId.value());
                ps.setLong(2, postIdValue);
                ps.setLong(3, parentCommentId.value());
                ps.setLong(4, authorId.value());
                ps.setString(5, content);
                ps.setShort(6, (short) 1); // depth = 1 for replies
                ps.setTimestamp(7, createdAt);
                ps.addBatch();
                batchCount++;

                if (batchCount >= batchSize) {
                    ps.executeBatch();
                    batchCount = 0;
                }

                if ((i + 1) % 1000 == 0) {
                    log.info("  Reply comments generated: {}/{}", i + 1, replyCount);
                }
            }

            if (batchCount > 0) {
                ps.executeBatch();
            }
            log.info("  Total comments generated: {} (top-level: {}, replies: {})",
                    topLevelCount + replyCount, topLevelCount, replyCount);
            return null;
        });

        return edges;
    }

    private int pickWeighted(double[] weights) {
        double target = random.nextDouble();
        double cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (target < cumulative) return i;
        }
        return weights.length - 1;
    }

    private String generateCommentContent() {
        String template = COMMENT_TEMPLATES[random.nextInt(COMMENT_TEMPLATES.length)];
        if (template.contains("%s")) {
            return String.format(template, TOPICS[random.nextInt(TOPICS.length)]);
        }
        return template;
    }

    private String generateReplyContent() {
        return REPLY_TEMPLATES[random.nextInt(REPLY_TEMPLATES.length)];
    }
}
