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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class UserGenerator {

    private static final Logger log = LoggerFactory.getLogger(UserGenerator.class);

    /** Pre-computed BCrypt hash of "password123" */
    private static final String PASSWORD_HASH =
            "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    private static final String INSERT_SQL =
            "INSERT INTO users (id, username, display_name, email, password_hash, " +
            "avatar_url, bio, visibility, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final GlobalIdGenerator idGen;
    private final Faker faker;
    private final Random random;
    private final int batchSize;

    public UserGenerator(JdbcTemplate jdbc, GlobalIdGenerator idGen, Faker faker,
                         Random random, int batchSize) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.faker = faker;
        this.random = random;
        this.batchSize = batchSize;
    }

    private static final String[] DEPARTMENTS = {
            "Engineering", "Marketing", "Sales", "Product", "HR",
            "Finance", "Legal", "Support", "Design", "Data Science",
            "DevOps", "Security", "QA", "Research", "Operations"
    };

    private static final String[] TITLES = {
            "Software Engineer", "Senior Software Engineer", "Staff Engineer",
            "Principal Engineer", "Engineering Manager", "Director of Engineering",
            "Product Manager", "Senior Product Manager", "Product Designer",
            "UX Researcher", "Data Analyst", "Data Scientist", "ML Engineer",
            "Marketing Manager", "Content Strategist", "Sales Representative",
            "Account Executive", "HR Business Partner", "Recruiter",
            "Technical Writer", "QA Engineer", "DevOps Engineer",
            "Security Engineer", "Financial Analyst", "Legal Counsel"
    };

    public List<GlobalId> generate(int count) {
        log.info("Generating {} users...", count);
        List<GlobalId> userIds = new ArrayList<>(count);
        Set<String> usedUsernames = new HashSet<>();
        Set<String> usedEmails = new HashSet<>();

        Instant now = Instant.now();
        // Users joined over the last 2 years
        long maxDaysAgo = 730;

        jdbc.execute(INSERT_SQL, (PreparedStatement ps) -> {
            int batchCount = 0;
            for (int i = 0; i < count; i++) {
                GlobalId id = idGen.next(ObjectType.USER);
                userIds.add(id);

                String firstName = faker.name().firstName();
                String lastName = faker.name().lastName();

                // Generate unique username
                String baseUsername = (firstName + "." + lastName).toLowerCase()
                        .replaceAll("[^a-z0-9.]", "");
                String username = baseUsername;
                int suffix = 1;
                while (usedUsernames.contains(username)) {
                    username = baseUsername + suffix++;
                }
                usedUsernames.add(username);

                // Generate unique email
                String baseEmail = (firstName.toLowerCase() + "." + lastName.toLowerCase())
                        .replaceAll("[^a-z0-9.]", "") + "@enterprise.com";
                String email = baseEmail;
                suffix = 1;
                while (usedEmails.contains(email)) {
                    email = (firstName.toLowerCase() + "." + lastName.toLowerCase())
                            .replaceAll("[^a-z0-9.]", "") + suffix++ + "@enterprise.com";
                }
                usedEmails.add(email);

                String displayName = firstName + " " + lastName;
                String bio = generateBio();
                String avatarUrl = "https://i.pravatar.cc/150?u=" + id.value();
                String visibility = "PUBLIC";

                // Stagger creation dates
                long daysAgo = (long) (random.nextDouble() * maxDaysAgo);
                Timestamp createdAt = Timestamp.from(now.minus(daysAgo, ChronoUnit.DAYS));
                Timestamp updatedAt = Timestamp.from(now.minus(
                        (long) (random.nextDouble() * daysAgo), ChronoUnit.DAYS));

                ps.setLong(1, id.value());
                ps.setString(2, username);
                ps.setString(3, displayName);
                ps.setString(4, email);
                ps.setString(5, PASSWORD_HASH);
                ps.setString(6, avatarUrl);
                ps.setString(7, bio);
                ps.setString(8, visibility);
                ps.setTimestamp(9, createdAt);
                ps.setTimestamp(10, updatedAt);
                ps.addBatch();
                batchCount++;

                if (batchCount >= batchSize) {
                    ps.executeBatch();
                    batchCount = 0;
                }

                if ((i + 1) % 1000 == 0 || (i + 1) == count) {
                    log.info("  Users generated: {}/{}", i + 1, count);
                }
            }
            if (batchCount > 0) {
                ps.executeBatch();
            }
            return null;
        });

        return userIds;
    }

    private String generateBio() {
        int style = random.nextInt(5);
        return switch (style) {
            case 0 -> faker.lorem().sentence(8) + " Passionate about " +
                       faker.programmingLanguage().name() + " and " +
                       faker.hobby().activity() + ".";
            case 1 -> TITLES[random.nextInt(TITLES.length)] + " with " +
                       (random.nextInt(15) + 1) + " years of experience. " +
                       faker.lorem().sentence(6);
            case 2 -> "Working on " + faker.app().name() + " | " +
                       faker.company().buzzword() + " enthusiast | " +
                       faker.address().city();
            case 3 -> faker.lorem().paragraph(1);
            default -> ""; // Some users have no bio
        };
    }
}
