package com.social.datagen.generator;

import com.social.core.id.GlobalId;
import com.social.core.model.MemberRole;
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
 * Generates follows (user-to-user) and memberships (user-to-team/group/page).
 * Uses preferential attachment for follows so popular users attract more followers.
 */
public class SocialGraphGenerator {

    private static final Logger log = LoggerFactory.getLogger(SocialGraphGenerator.class);

    private static final String INSERT_FOLLOW_SQL =
            "INSERT INTO follows (follower_id, followed_id, created_at) VALUES (?, ?, ?)";

    private static final String INSERT_MEMBERSHIP_SQL =
            "INSERT INTO memberships (user_id, group_id, role, joined_at) VALUES (?, ?, ?, ?)";

    private static final String INSERT_PAGE_FOLLOW_SQL =
            "INSERT INTO follows (follower_id, followed_id, created_at) VALUES (?, ?, ?)";

    /** Edge records exported for AOEE sync. */
    public record FollowEdge(long followerId, long followedId) {}
    public record MembershipEdge(long entityId, long userId, String entityType) {}

    private final JdbcTemplate jdbc;
    private final Random random;
    private final int batchSize;

    public SocialGraphGenerator(JdbcTemplate jdbc, Random random, int batchSize) {
        this.jdbc = jdbc;
        this.random = random;
        this.batchSize = batchSize;
    }

    /**
     * Generate user-to-user follows with preferential attachment.
     * Each user follows 5-30 others, with popular users being more likely targets.
     */
    public List<FollowEdge> generateFollows(List<GlobalId> userIds) {
        int userCount = userIds.size();
        log.info("Generating follows for {} users (preferential attachment)...", userCount);

        // Track follower counts for preferential attachment
        int[] followerCounts = new int[userCount];
        // Seed: first 10% of users start with higher base popularity
        for (int i = 0; i < userCount / 10; i++) {
            followerCounts[i] = 5 + random.nextInt(10);
        }

        List<FollowEdge> edges = new ArrayList<>();
        Set<Long> followedSet = new HashSet<>(); // per-user dedup

        jdbc.execute(INSERT_FOLLOW_SQL, (PreparedStatement ps) -> {
            int batchCount = 0;
            int totalFollows = 0;
            Instant now = Instant.now();

            for (int i = 0; i < userCount; i++) {
                GlobalId userId = userIds.get(i);
                int followCount = 5 + random.nextInt(26); // 5-30 follows
                followedSet.clear();

                for (int f = 0; f < followCount; f++) {
                    int targetIdx = pickByPreferentialAttachment(followerCounts, userCount, i);
                    GlobalId targetId = userIds.get(targetIdx);

                    if (followedSet.contains(targetId.value())) continue;
                    followedSet.add(targetId.value());

                    followerCounts[targetIdx]++;
                    edges.add(new FollowEdge(userId.value(), targetId.value()));

                    Timestamp createdAt = Timestamp.from(
                            now.minus(random.nextInt(365) + 1L, ChronoUnit.DAYS));

                    ps.setLong(1, userId.value());
                    ps.setLong(2, targetId.value());
                    ps.setTimestamp(3, createdAt);
                    ps.addBatch();
                    batchCount++;
                    totalFollows++;

                    if (batchCount >= batchSize) {
                        ps.executeBatch();
                        batchCount = 0;
                    }
                }

                if ((i + 1) % 1000 == 0) {
                    log.info("  Follow generation progress: {}/{} users processed, {} follows total",
                            i + 1, userCount, totalFollows);
                }
            }
            if (batchCount > 0) {
                ps.executeBatch();
            }
            log.info("  Total follows generated: {}", totalFollows);
            return null;
        });

        return edges;
    }

    /**
     * Pick a target using preferential attachment (popular users get picked more).
     * Excludes the source user (no self-follows).
     */
    private int pickByPreferentialAttachment(int[] followerCounts, int size, int excludeIdx) {
        // Base weight of 1 ensures even zero-follower users have a chance
        int totalWeight = 0;
        for (int i = 0; i < size; i++) {
            if (i != excludeIdx) {
                totalWeight += followerCounts[i] + 1;
            }
        }

        int target = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < size; i++) {
            if (i == excludeIdx) continue;
            cumulative += followerCounts[i] + 1;
            if (target < cumulative) {
                return i;
            }
        }
        // Fallback (shouldn't reach here)
        return (excludeIdx + 1) % size;
    }

    /**
     * Generate team memberships: each user joins 2-5 teams.
     */
    public List<MembershipEdge> generateTeamMemberships(List<GlobalId> userIds,
                                                         List<GlobalId> teamIds) {
        log.info("Generating team memberships...");
        List<MembershipEdge> edges = new ArrayList<>();
        Instant now = Instant.now();

        jdbc.execute(INSERT_MEMBERSHIP_SQL, (PreparedStatement ps) -> {
            int batchCount = 0;
            Set<String> assigned = new HashSet<>();

            // First user of each team is OWNER
            for (int t = 0; t < teamIds.size(); t++) {
                GlobalId teamId = teamIds.get(t);
                GlobalId ownerId = userIds.get(t % userIds.size());
                String key = ownerId.value() + ":" + teamId.value();

                if (assigned.add(key)) {
                    edges.add(new MembershipEdge(teamId.value(), ownerId.value(), "TEAM"));
                    ps.setLong(1, ownerId.value());
                    ps.setLong(2, teamId.value());
                    ps.setString(3, MemberRole.OWNER.name());
                    ps.setTimestamp(4, Timestamp.from(
                            now.minus(random.nextInt(700) + 30L, ChronoUnit.DAYS)));
                    ps.addBatch();
                    batchCount++;
                }
            }

            // Each user joins 2-5 teams
            for (int i = 0; i < userIds.size(); i++) {
                GlobalId userId = userIds.get(i);
                int teamCount = 2 + random.nextInt(4);

                for (int t = 0; t < teamCount; t++) {
                    GlobalId teamId = teamIds.get(random.nextInt(teamIds.size()));
                    String key = userId.value() + ":" + teamId.value();

                    if (!assigned.add(key)) continue;

                    MemberRole role = random.nextInt(20) == 0
                            ? MemberRole.ADMIN : MemberRole.MEMBER;
                    edges.add(new MembershipEdge(teamId.value(), userId.value(), "TEAM"));

                    ps.setLong(1, userId.value());
                    ps.setLong(2, teamId.value());
                    ps.setString(3, role.name());
                    ps.setTimestamp(4, Timestamp.from(
                            now.minus(random.nextInt(500) + 1L, ChronoUnit.DAYS)));
                    ps.addBatch();
                    batchCount++;

                    if (batchCount >= batchSize) {
                        ps.executeBatch();
                        batchCount = 0;
                    }
                }
            }

            if (batchCount > 0) {
                ps.executeBatch();
            }
            log.info("  Team memberships generated: {}", edges.size());
            return null;
        });

        return edges;
    }

    /**
     * Generate group memberships: each user joins 0-3 groups.
     */
    public List<MembershipEdge> generateGroupMemberships(List<GlobalId> userIds,
                                                          List<GlobalId> groupIds) {
        log.info("Generating group memberships...");
        List<MembershipEdge> edges = new ArrayList<>();
        Instant now = Instant.now();

        jdbc.execute(INSERT_MEMBERSHIP_SQL, (PreparedStatement ps) -> {
            int batchCount = 0;
            Set<String> assigned = new HashSet<>();

            // Owners for each group
            for (int g = 0; g < groupIds.size(); g++) {
                GlobalId groupId = groupIds.get(g);
                GlobalId ownerId = userIds.get((g * 3) % userIds.size());
                String key = ownerId.value() + ":" + groupId.value();

                if (assigned.add(key)) {
                    edges.add(new MembershipEdge(groupId.value(), ownerId.value(), "GROUP"));
                    ps.setLong(1, ownerId.value());
                    ps.setLong(2, groupId.value());
                    ps.setString(3, MemberRole.OWNER.name());
                    ps.setTimestamp(4, Timestamp.from(
                            now.minus(random.nextInt(400) + 10L, ChronoUnit.DAYS)));
                    ps.addBatch();
                    batchCount++;
                }
            }

            // Each user joins 0-3 groups
            for (int i = 0; i < userIds.size(); i++) {
                GlobalId userId = userIds.get(i);
                int groupCount = random.nextInt(4); // 0-3

                for (int g = 0; g < groupCount; g++) {
                    GlobalId groupId = groupIds.get(random.nextInt(groupIds.size()));
                    String key = userId.value() + ":" + groupId.value();

                    if (!assigned.add(key)) continue;

                    edges.add(new MembershipEdge(groupId.value(), userId.value(), "GROUP"));
                    ps.setLong(1, userId.value());
                    ps.setLong(2, groupId.value());
                    ps.setString(3, MemberRole.MEMBER.name());
                    ps.setTimestamp(4, Timestamp.from(
                            now.minus(random.nextInt(300) + 1L, ChronoUnit.DAYS)));
                    ps.addBatch();
                    batchCount++;

                    if (batchCount >= batchSize) {
                        ps.executeBatch();
                        batchCount = 0;
                    }
                }
            }

            if (batchCount > 0) {
                ps.executeBatch();
            }
            log.info("  Group memberships generated: {}", edges.size());
            return null;
        });

        return edges;
    }

    /**
     * Generate page follows: each user follows 1-5 pages.
     */
    public List<MembershipEdge> generatePageFollows(List<GlobalId> userIds,
                                                     List<GlobalId> pageIds) {
        log.info("Generating page follows...");
        List<MembershipEdge> edges = new ArrayList<>();
        Instant now = Instant.now();

        jdbc.execute(INSERT_PAGE_FOLLOW_SQL, (PreparedStatement ps) -> {
            int batchCount = 0;
            Set<String> assigned = new HashSet<>();

            for (int i = 0; i < userIds.size(); i++) {
                GlobalId userId = userIds.get(i);
                int pageCount = 1 + random.nextInt(5); // 1-5

                for (int p = 0; p < pageCount; p++) {
                    GlobalId pageId = pageIds.get(random.nextInt(pageIds.size()));
                    String key = userId.value() + ":" + pageId.value();

                    if (!assigned.add(key)) continue;

                    edges.add(new MembershipEdge(pageId.value(), userId.value(), "PAGE"));
                    ps.setLong(1, userId.value());
                    ps.setLong(2, pageId.value());
                    ps.setTimestamp(3, Timestamp.from(
                            now.minus(random.nextInt(365) + 1L, ChronoUnit.DAYS)));
                    ps.addBatch();
                    batchCount++;

                    if (batchCount >= batchSize) {
                        ps.executeBatch();
                        batchCount = 0;
                    }
                }
            }

            if (batchCount > 0) {
                ps.executeBatch();
            }
            log.info("  Page follows generated: {}", edges.size());
            return null;
        });

        return edges;
    }
}
