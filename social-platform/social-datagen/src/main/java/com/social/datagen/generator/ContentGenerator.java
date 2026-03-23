package com.social.datagen.generator;

import com.social.core.id.GlobalId;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
import com.social.core.model.TargetType;
import com.social.core.model.Visibility;
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
 * Generates posts distributed across user feeds, team feeds, page feeds,
 * group feeds, and project feeds with enterprise-appropriate content.
 */
public class ContentGenerator {

    private static final Logger log = LoggerFactory.getLogger(ContentGenerator.class);

    private static final String INSERT_POST_SQL =
            "INSERT INTO posts (id, author_id, content, target_type, target_id, " +
            "visibility, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    /** Authorship edge for AOEE sync */
    public record AuthorshipEdge(long authorId, long postId) {}

    private final JdbcTemplate jdbc;
    private final GlobalIdGenerator idGen;
    private final Faker faker;
    private final Random random;
    private final int batchSize;

    // Enterprise content templates
    private static final String[] PROJECT_UPDATES = {
            "Excited to share that we've completed the %s milestone! Great work by the team.",
            "Sprint review update: We shipped %s this week. Here's what we learned...",
            "Just deployed %s to production. Monitoring dashboards look green!",
            "Big milestone: %s is now feature-complete. Moving to QA next week.",
            "Wrapped up the design review for %s. Thanks everyone for the great feedback.",
            "Performance improvements for %s are live - 40%% faster response times!",
            "Architecture proposal for %s is ready for review. Please share your thoughts.",
            "We've resolved the %s issue that was affecting customers. Root cause analysis posted.",
    };

    private static final String[] ANNOUNCEMENTS = {
            "Reminder: All-hands meeting this Friday at 2pm. Agenda in the comments.",
            "Welcome to our new team members who joined this week! Please introduce yourselves.",
            "Updated: Our work-from-home policy has been refreshed. Check the HR page for details.",
            "Congratulations to the team on winning the innovation award!",
            "Office closure: The building will be closed next Monday for maintenance.",
            "New tool alert: We've rolled out a new project management tool. Training sessions available.",
            "Quarterly results are in - we exceeded our targets! Details in the all-hands deck.",
            "Important: Security training is due by end of month. Please complete ASAP.",
    };

    private static final String[] QUESTIONS = {
            "Has anyone worked with %s before? Looking for advice on best practices.",
            "Quick question: What's the recommended approach for %s?",
            "Seeking feedback on my proposal for %s. Would love your input!",
            "Does anyone know who owns the %s service? Need to discuss an integration.",
            "What tools do you all use for %s? Evaluating options for our team.",
            "Looking for a mentor in %s. Anyone interested?",
            "Anyone experienced issues with %s today? Seems intermittent.",
    };

    private static final String[] KUDOS = {
            "Huge shoutout to %s for going above and beyond on the release!",
            "Want to recognize %s for their incredible mentorship this quarter.",
            "Thanks to %s for jumping in and helping debug that critical issue at 2am.",
            "Kudos to the entire %s team for delivering ahead of schedule!",
            "Appreciation post: %s has been an absolute rockstar on the project.",
    };

    private static final String[] EVENT_NOTICES = {
            "Join us for the tech talk on %s this Thursday at noon!",
            "Hackathon sign-ups are open! This quarter's theme: %s",
            "Lunch & Learn: %s - sign up in the comments.",
            "Team offsite planning: Please fill out the preference survey by Friday.",
            "Happy hour this Friday at 5pm. Bring your favorite %s stories!",
    };

    private static final String[] TECH_TOPICS = {
            "microservices", "Kubernetes", "GraphQL", "machine learning",
            "data pipelines", "observability", "CI/CD", "API design",
            "cloud migration", "performance tuning", "security hardening",
            "database optimization", "event-driven architecture", "React",
            "TypeScript", "Rust", "system design", "distributed systems"
    };

    public ContentGenerator(JdbcTemplate jdbc, GlobalIdGenerator idGen, Faker faker,
                            Random random, int batchSize) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.faker = faker;
        this.random = random;
        this.batchSize = batchSize;
    }

    /**
     * Generate posts with realistic distribution across feed targets.
     * Activity levels vary: ~10% of users create ~50% of content (power users).
     */
    public List<GlobalId> generate(int count, List<GlobalId> userIds,
                                    List<GlobalId> teamIds, List<GlobalId> pageIds,
                                    List<GlobalId> groupIds, List<GlobalId> projectIds,
                                    List<AuthorshipEdge> authorshipEdges) {
        log.info("Generating {} posts...", count);
        List<GlobalId> postIds = new ArrayList<>(count);

        // Build power-user distribution: first 10% get 5x weight
        int[] userWeights = new int[userIds.size()];
        int powerUserCount = Math.max(1, userIds.size() / 10);
        for (int i = 0; i < userIds.size(); i++) {
            userWeights[i] = i < powerUserCount ? 5 : 1;
        }
        int totalWeight = 0;
        for (int w : userWeights) totalWeight += w;

        Instant now = Instant.now();

        int finalTotalWeight = totalWeight;
        jdbc.execute(INSERT_POST_SQL, (PreparedStatement ps) -> {
            int batchCount = 0;

            for (int i = 0; i < count; i++) {
                GlobalId postId = idGen.next(ObjectType.POST);
                postIds.add(postId);

                // Pick author with power-user bias
                int authorIdx = pickWeighted(userWeights, finalTotalWeight);
                GlobalId authorId = userIds.get(authorIdx);
                authorshipEdges.add(new AuthorshipEdge(authorId.value(), postId.value()));

                // Determine target: 60% user, 15% team, 10% page, 10% group, 5% project
                double roll = random.nextDouble();
                TargetType targetType;
                long targetId;

                if (roll < 0.60) {
                    targetType = TargetType.USER_FEED;
                    targetId = authorId.value();
                } else if (roll < 0.75) {
                    targetType = TargetType.TEAM_FEED;
                    targetId = teamIds.get(random.nextInt(teamIds.size())).value();
                } else if (roll < 0.85) {
                    targetType = TargetType.PAGE_FEED;
                    targetId = pageIds.get(random.nextInt(pageIds.size())).value();
                } else if (roll < 0.95) {
                    targetType = TargetType.GROUP_FEED;
                    targetId = groupIds.get(random.nextInt(groupIds.size())).value();
                } else {
                    targetType = TargetType.PROJECT_FEED;
                    targetId = projectIds.get(random.nextInt(projectIds.size())).value();
                }

                String content = generatePostContent();
                Visibility visibility = targetType == TargetType.USER_FEED
                        ? Visibility.PUBLIC
                        : (random.nextInt(10) < 8 ? Visibility.PUBLIC : Visibility.TEAM_VISIBLE);

                // Posts spread over last 6 months, more recent posts more likely
                long hoursAgo = (long) Math.pow(random.nextDouble(), 0.5) * 4380; // sqrt bias toward recent
                Timestamp createdAt = Timestamp.from(now.minus(hoursAgo, ChronoUnit.HOURS));

                ps.setLong(1, postId.value());
                ps.setLong(2, authorId.value());
                ps.setString(3, content);
                ps.setString(4, targetType.name());
                ps.setLong(5, targetId);
                ps.setString(6, visibility.name());
                ps.setTimestamp(7, createdAt);
                ps.setTimestamp(8, createdAt);
                ps.addBatch();
                batchCount++;

                if (batchCount >= batchSize) {
                    ps.executeBatch();
                    batchCount = 0;
                }

                if ((i + 1) % 1000 == 0 || (i + 1) == count) {
                    log.info("  Posts generated: {}/{}", i + 1, count);
                }
            }

            if (batchCount > 0) {
                ps.executeBatch();
            }
            return null;
        });

        return postIds;
    }

    private int pickWeighted(int[] weights, int totalWeight) {
        int target = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (target < cumulative) return i;
        }
        return weights.length - 1;
    }

    private String generatePostContent() {
        String topic = TECH_TOPICS[random.nextInt(TECH_TOPICS.length)];
        String person = faker.name().fullName();

        int style = random.nextInt(10);
        String base;

        if (style < 3) {
            // Project update (30%)
            base = String.format(
                    PROJECT_UPDATES[random.nextInt(PROJECT_UPDATES.length)], topic);
        } else if (style < 5) {
            // Announcement (20%)
            base = ANNOUNCEMENTS[random.nextInt(ANNOUNCEMENTS.length)];
        } else if (style < 7) {
            // Question (20%)
            base = String.format(
                    QUESTIONS[random.nextInt(QUESTIONS.length)], topic);
        } else if (style < 9) {
            // Kudos (20%)
            base = String.format(
                    KUDOS[random.nextInt(KUDOS.length)],
                    random.nextBoolean() ? person : topic);
        } else {
            // Event notice (10%)
            base = String.format(
                    EVENT_NOTICES[random.nextInt(EVENT_NOTICES.length)], topic);
        }

        // Sometimes add extra detail
        if (random.nextInt(3) == 0) {
            base += "\n\n" + faker.lorem().paragraph(1);
        }

        return base;
    }
}
