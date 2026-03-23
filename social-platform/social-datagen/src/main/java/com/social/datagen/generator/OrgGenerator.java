package com.social.datagen.generator;

import com.social.core.id.GlobalId;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
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

public class OrgGenerator {

    private static final Logger log = LoggerFactory.getLogger(OrgGenerator.class);

    private static final String INSERT_TEAM_SQL =
            "INSERT INTO teams (id, name, slug, description, visibility, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String INSERT_GROUP_SQL =
            "INSERT INTO groups_ (id, name, slug, description, visibility, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String INSERT_PAGE_SQL =
            "INSERT INTO pages (id, name, slug, description, avatar_url, visibility, owner_type, owner_id, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_PROJECT_SQL =
            "INSERT INTO projects (id, name, slug, description, visibility, page_id, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String[] TEAM_NAMES = {
            "Engineering", "Frontend Engineering", "Backend Engineering", "Platform Engineering",
            "Mobile Engineering", "Marketing", "Growth Marketing", "Content Marketing",
            "Sales", "Enterprise Sales", "SMB Sales", "Product", "Product Design",
            "HR", "People Operations", "Finance", "Accounting", "Legal",
            "Compliance", "Support", "Customer Success", "Design", "Brand Design",
            "Data Science", "Machine Learning", "DevOps", "SRE", "Security",
            "QA", "Research", "Operations", "IT", "Procurement",
            "Partnerships", "Business Development", "Analytics", "Infrastructure",
            "Developer Experience", "Technical Writing", "Program Management"
    };

    private static final String[] GROUP_PREFIXES = {
            "Coffee Lovers", "Book Club", "Running Group", "Photography",
            "Board Games", "Volunteering", "Mentorship", "Women in Tech",
            "Parents Group", "Sustainability", "Diversity & Inclusion",
            "New Employees", "Remote Workers", "Pet Lovers", "Music Fans",
            "Cooking Club", "Fitness Challenge", "Movie Nights", "Hackathon",
            "Open Source", "Learning & Development", "Career Growth",
            "Innovation Lab", "Tech Talks", "Wellness", "Social Impact",
            "LGBTQ+ Alliance", "Veterans Network", "Accessibility Champions"
    };

    private static final String[] PAGE_NAMES = {
            "Company Announcements", "Engineering Blog", "Product Updates",
            "CEO Corner", "HR Updates", "IT Service Desk", "Benefits & Perks",
            "Office Events", "Learning Resources", "Security Advisories",
            "Release Notes", "Architecture Decision Records", "On-Call Handbook",
            "New Hire Guide", "Travel Policy", "Expense Guidelines",
            "Brand Guidelines", "API Documentation", "Platform Status",
            "Quarterly Goals", "All-Hands Recaps", "Team Spotlights",
            "Customer Stories", "Industry News", "Tech Radar"
    };

    private static final String[] PROJECT_NAMES = {
            "Project Phoenix", "Project Atlas", "Project Horizon", "Project Nova",
            "Project Mercury", "Project Catalyst", "Project Compass", "Project Summit",
            "Project Lighthouse", "Project Velocity", "Q1 Platform Migration",
            "Customer Portal Redesign", "Mobile App v3", "Data Pipeline Overhaul",
            "Search Infrastructure", "Auth Service Rewrite", "Analytics Dashboard",
            "Notification System", "Payment Integration", "API Gateway v2",
            "Performance Optimization", "Accessibility Audit", "SOC2 Compliance",
            "Cloud Migration", "Microservices Decomposition"
    };

    private final JdbcTemplate jdbc;
    private final GlobalIdGenerator idGen;
    private final Faker faker;
    private final Random random;
    private final int batchSize;

    public OrgGenerator(JdbcTemplate jdbc, GlobalIdGenerator idGen, Faker faker,
                        Random random, int batchSize) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.faker = faker;
        this.random = random;
        this.batchSize = batchSize;
    }

    public List<GlobalId> generateTeams(int count) {
        log.info("Generating {} teams...", count);
        List<GlobalId> ids = new ArrayList<>(count);
        Instant now = Instant.now();

        jdbc.execute(INSERT_TEAM_SQL, (PreparedStatement ps) -> {
            for (int i = 0; i < count; i++) {
                GlobalId id = idGen.next(ObjectType.TEAM);
                ids.add(id);

                String name = i < TEAM_NAMES.length ? TEAM_NAMES[i]
                        : TEAM_NAMES[random.nextInt(TEAM_NAMES.length)] + " " + (i + 1);
                String description = "The " + name + " team. " + faker.lorem().sentence(10);
                Visibility visibility = random.nextInt(10) < 8
                        ? Visibility.PUBLIC : Visibility.TEAM_VISIBLE;
                Timestamp createdAt = Timestamp.from(
                        now.minus(random.nextInt(730) + 30L, ChronoUnit.DAYS));

                String slug = name.toLowerCase().replaceAll(" ", "-");

                ps.setLong(1, id.value());
                ps.setString(2, name);
                ps.setString(3, slug);
                ps.setString(4, description);
                ps.setString(5, visibility.name());
                ps.setTimestamp(6, createdAt);
                ps.addBatch();
            }
            ps.executeBatch();
            return null;
        });

        log.info("  Teams generated: {}", count);
        return ids;
    }

    public List<GlobalId> generateGroups(int count) {
        log.info("Generating {} groups...", count);
        List<GlobalId> ids = new ArrayList<>(count);
        Instant now = Instant.now();

        jdbc.execute(INSERT_GROUP_SQL, (PreparedStatement ps) -> {
            int batchCount = 0;
            for (int i = 0; i < count; i++) {
                GlobalId id = idGen.next(ObjectType.GROUP);
                ids.add(id);

                String name = i < GROUP_PREFIXES.length ? GROUP_PREFIXES[i]
                        : GROUP_PREFIXES[random.nextInt(GROUP_PREFIXES.length)] + " " +
                          faker.color().name();
                String description = faker.lorem().sentence(12);
                Visibility visibility = random.nextInt(10) < 6
                        ? Visibility.PUBLIC : Visibility.RESTRICTED;
                Timestamp createdAt = Timestamp.from(
                        now.minus(random.nextInt(500) + 10L, ChronoUnit.DAYS));

                String slug = name.toLowerCase().replaceAll(" ", "-");

                ps.setLong(1, id.value());
                ps.setString(2, name);
                ps.setString(3, slug);
                ps.setString(4, description);
                ps.setString(5, visibility.name());
                ps.setTimestamp(6, createdAt);
                ps.addBatch();
                batchCount++;

                if (batchCount >= batchSize) {
                    ps.executeBatch();
                    batchCount = 0;
                }
            }
            if (batchCount > 0) {
                ps.executeBatch();
            }
            return null;
        });

        log.info("  Groups generated: {}", count);
        return ids;
    }

    public List<GlobalId> generatePages(int count) {
        log.info("Generating {} pages...", count);
        List<GlobalId> ids = new ArrayList<>(count);
        Instant now = Instant.now();

        jdbc.execute(INSERT_PAGE_SQL, (PreparedStatement ps) -> {
            for (int i = 0; i < count; i++) {
                GlobalId id = idGen.next(ObjectType.PAGE);
                ids.add(id);

                String name = i < PAGE_NAMES.length ? PAGE_NAMES[i]
                        : PAGE_NAMES[random.nextInt(PAGE_NAMES.length)] + " - " +
                          faker.company().buzzword();
                String slug = name.toLowerCase().replaceAll(" ", "-");
                String description = faker.lorem().sentence(15);
                String avatarUrl = "https://i.pravatar.cc/150?u=page-" + id.value();
                Visibility visibility = Visibility.PUBLIC;
                Timestamp createdAt = Timestamp.from(
                        now.minus(random.nextInt(600) + 60L, ChronoUnit.DAYS));

                ps.setLong(1, id.value());
                ps.setString(2, name);
                ps.setString(3, slug);
                ps.setString(4, description);
                ps.setString(5, avatarUrl);
                ps.setString(6, visibility.name());
                ps.setString(7, "USER");
                ps.setLong(8, id.value()); // owner_id placeholder
                ps.setTimestamp(9, createdAt);
                ps.addBatch();
            }
            ps.executeBatch();
            return null;
        });

        log.info("  Pages generated: {}", count);
        return ids;
    }

    public List<GlobalId> generateProjects(int count, List<GlobalId> pageIds) {
        log.info("Generating {} projects...", count);
        List<GlobalId> ids = new ArrayList<>(count);
        Instant now = Instant.now();

        jdbc.execute(INSERT_PROJECT_SQL, (PreparedStatement ps) -> {
            for (int i = 0; i < count; i++) {
                GlobalId id = idGen.next(ObjectType.PROJECT);
                ids.add(id);

                String name = i < PROJECT_NAMES.length ? PROJECT_NAMES[i]
                        : "Project " + faker.hacker().adjective() + " " +
                          faker.hacker().noun();
                String slug = name.toLowerCase().replaceAll(" ", "-");
                String description = faker.lorem().paragraph(2);
                Visibility visibility = random.nextInt(10) < 7
                        ? Visibility.PUBLIC : Visibility.TEAM_VISIBLE;
                GlobalId pageId = pageIds.get(i % pageIds.size());
                Timestamp createdAt = Timestamp.from(
                        now.minus(random.nextInt(365) + 5L, ChronoUnit.DAYS));

                ps.setLong(1, id.value());
                ps.setString(2, name);
                ps.setString(3, slug);
                ps.setString(4, description);
                ps.setString(5, visibility.name());
                ps.setLong(6, pageId.value());
                ps.setTimestamp(7, createdAt);
                ps.addBatch();
            }
            ps.executeBatch();
            return null;
        });

        log.info("  Projects generated: {}", count);
        return ids;
    }
}
