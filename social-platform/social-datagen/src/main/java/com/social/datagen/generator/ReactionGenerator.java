package com.social.datagen.generator;

import com.social.core.id.GlobalId;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import com.social.core.model.ReactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Generates reactions on posts and comments with power-law distribution.
 * Reaction types: mostly LIKE (60%), LOVE (20%), others split the rest.
 */
public class ReactionGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReactionGenerator.class);

    private static final String INSERT_REACTION_SQL =
            "INSERT INTO reactions (id, target_id, target_type, user_id, reaction_type, " +
            "created_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (target_id, user_id) DO NOTHING";

    /** Edge for AOEE sync */
    public record ReactionEdge(long userId, long targetId, ReactionType type) {}

    private final JdbcTemplate jdbc;
    private final GlobalIdGenerator idGen;
    private final Random random;
    private final int batchSize;

    public ReactionGenerator(JdbcTemplate jdbc, GlobalIdGenerator idGen,
                             Random random, int batchSize) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.random = random;
        this.batchSize = batchSize;
    }

    /**
     * Generate reactions with power-law distribution.
     * 80% on posts, 20% on comments.
     */
    public List<ReactionEdge> generate(int count, List<GlobalId> postIds,
                                        List<GlobalId> userIds) {
        log.info("Generating {} reactions...", count);
        List<ReactionEdge> edges = new ArrayList<>();

        // Power-law weights for posts (earlier posts in list = older = more reactions accumulated)
        int postCount = postIds.size();
        double[] postWeights = new double[postCount];
        for (int i = 0; i < postCount; i++) {
            // Zipf distribution: popular posts get disproportionate reactions
            double rank = i + 1;
            postWeights[i] = 1.0 / Math.pow(rank, 0.6);
        }
        double totalPostWeight = 0;
        for (double w : postWeights) totalPostWeight += w;
        for (int i = 0; i < postCount; i++) {
            postWeights[i] /= totalPostWeight;
        }

        jdbc.execute(INSERT_REACTION_SQL, (PreparedStatement ps) -> {
            int batchCount = 0;
            int generated = 0;
            Instant now = Instant.now();
            Set<String> dedup = new HashSet<>();

            while (generated < count) {
                GlobalId reactionId = idGen.next(ObjectType.REACTION);
                GlobalId userId = userIds.get(random.nextInt(userIds.size()));

                // 80% on posts, 20% on comments (using post IDs as proxy for comment targets)
                String targetTypeStr;
                long targetId;

                if (random.nextDouble() < 0.80) {
                    targetTypeStr = "POST";
                    int postIdx = pickWeighted(postWeights);
                    targetId = postIds.get(postIdx).value();
                } else {
                    targetTypeStr = "COMMENT";
                    // Use comment-range IDs; approximate by picking from post range
                    // (actual comment IDs would require tracking, but this is sufficient for data gen)
                    int postIdx = random.nextInt(postCount);
                    targetId = postIds.get(postIdx).value();
                }

                // Dedup: same user can't react twice to same target
                String dedupKey = userId.value() + ":" + targetTypeStr + ":" + targetId;
                if (!dedup.add(dedupKey)) continue;

                ReactionType reactionType = pickReactionType();
                edges.add(new ReactionEdge(userId.value(), targetId, reactionType));

                Timestamp createdAt = Timestamp.from(
                        now.minus((long) (random.nextDouble() * 4000), ChronoUnit.HOURS));

                ps.setLong(1, reactionId.value());
                ps.setLong(2, targetId);
                ps.setString(3, targetTypeStr);
                ps.setLong(4, userId.value());
                ps.setString(5, reactionType.name());
                ps.setTimestamp(6, createdAt);
                ps.addBatch();
                batchCount++;
                generated++;

                if (batchCount >= batchSize) {
                    ps.executeBatch();
                    batchCount = 0;
                }

                if (generated % 1000 == 0 || generated == count) {
                    log.info("  Reactions generated: {}/{}", generated, count);
                }
            }

            if (batchCount > 0) {
                ps.executeBatch();
            }
            return null;
        });

        return edges;
    }

    private ReactionType pickReactionType() {
        double roll = random.nextDouble();
        if (roll < 0.60) return ReactionType.LIKE;
        if (roll < 0.80) return ReactionType.LOVE;
        if (roll < 0.88) return ReactionType.HAHA;
        if (roll < 0.93) return ReactionType.WOW;
        if (roll < 0.97) return ReactionType.SAD;
        return ReactionType.ANGRY;
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
}
