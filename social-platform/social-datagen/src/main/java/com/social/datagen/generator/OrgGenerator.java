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

    /**
     * Generate a realistic org structure: Company → Divisions → Departments → Teams
     * Assigns users to positions with reporting lines and dotted-line relationships.
     */
    public void generateOrgStructure(List<GlobalId> userIds) {
        log.info("Generating org structure...");
        if (userIds.size() < 20) {
            log.warn("  Not enough users ({}) for org structure, skipping", userIds.size());
            return;
        }

        int idx = 0; // pointer into userIds for assigning people

        // 1. Company root
        long companyId = idGen.next(ObjectType.ORG_UNIT).value();
        long ceoUserId = userIds.get(idx++).value();
        insertOrgUnit(companyId, "WorkSphere Inc.", "COMPANY", null, ceoUserId, "Global enterprise social platform company", "CORP-001");
        insertAssignment(userIds.get(0).value(), ceoUserId, companyId, "Chief Executive Officer", "SOLID", null, "CEO");

        // 2. C-Suite under CEO
        String[][] cSuite = {
            {"Office of the CFO", "Chief Financial Officer", "C_SUITE"},
            {"Office of the CTO", "Chief Technology Officer", "C_SUITE"},
            {"Office of the COO", "Chief Operating Officer", "C_SUITE"},
            {"Office of the CPO", "Chief Product Officer", "C_SUITE"},
            {"Office of the CHRO", "Chief Human Resources Officer", "C_SUITE"},
        };
        long[] cSuiteUnitIds = new long[cSuite.length];
        long[] cSuiteUserIds = new long[cSuite.length];
        for (int i = 0; i < cSuite.length && idx < userIds.size(); i++) {
            cSuiteUnitIds[i] = idGen.next(ObjectType.ORG_UNIT).value();
            cSuiteUserIds[i] = userIds.get(idx++).value();
            insertOrgUnit(cSuiteUnitIds[i], cSuite[i][0], "DIVISION", companyId, cSuiteUserIds[i], null, "DIV-" + (i + 1));
            insertAssignment(idGen.next(ObjectType.ORG_ASSIGNMENT).value(), cSuiteUserIds[i], cSuiteUnitIds[i], cSuite[i][1], "SOLID", ceoUserId, cSuite[i][2]);
        }

        // 3. Departments under each division
        String[][][] departments = {
            // Under CFO
            {{"Finance", "VP of Finance", "VP"}, {"Accounting", "Director of Accounting", "DIRECTOR"}, {"FP&A", "Director of FP&A", "DIRECTOR"}},
            // Under CTO
            {{"Engineering", "VP of Engineering", "VP"}, {"Platform", "VP of Platform", "VP"}, {"Security", "Director of Security", "DIRECTOR"}, {"Data Science", "VP of Data Science", "VP"}},
            // Under COO
            {{"Operations", "VP of Operations", "VP"}, {"IT", "Director of IT", "DIRECTOR"}, {"Facilities", "Director of Facilities", "DIRECTOR"}},
            // Under CPO
            {{"Product", "VP of Product", "VP"}, {"Design", "VP of Design", "VP"}, {"Research", "Director of Research", "DIRECTOR"}},
            // Under CHRO
            {{"People Ops", "VP of People Ops", "VP"}, {"Talent Acquisition", "Director of TA", "DIRECTOR"}, {"Learning & Dev", "Director of L&D", "DIRECTOR"}},
        };

        List<long[]> deptList = new ArrayList<>(); // [deptUnitId, deptHeadUserId, divisionIdx]
        for (int d = 0; d < departments.length && d < cSuiteUnitIds.length; d++) {
            for (String[] dept : departments[d]) {
                if (idx >= userIds.size()) break;
                long deptId = idGen.next(ObjectType.ORG_UNIT).value();
                long headId = userIds.get(idx++).value();
                insertOrgUnit(deptId, dept[0], "DEPARTMENT", cSuiteUnitIds[d], headId, null, null);
                insertAssignment(idGen.next(ObjectType.ORG_ASSIGNMENT).value(), headId, deptId, dept[1], "SOLID", cSuiteUserIds[d], dept[2]);
                deptList.add(new long[]{deptId, headId, d});
            }
        }

        // 4. Teams under departments, assign remaining users as managers and ICs
        String[] teamSuffixes = {"Alpha", "Beta", "Core", "Growth", "Platform", "Infrastructure", "Mobile", "Web"};
        List<long[]> managerList = new ArrayList<>(); // [managerUserId, teamUnitId]

        for (long[] dept : deptList) {
            long deptId = dept[0];
            long deptHead = dept[1];
            // Create 1-2 teams per department
            int numTeams = 1 + random.nextInt(2);
            for (int t = 0; t < numTeams && idx < userIds.size(); t++) {
                long teamId = idGen.next(ObjectType.ORG_UNIT).value();
                long managerId = userIds.get(idx++).value();
                String teamName = teamSuffixes[random.nextInt(teamSuffixes.length)] + " Team";
                insertOrgUnit(teamId, teamName, "TEAM", deptId, managerId, null, null);
                insertAssignment(idGen.next(ObjectType.ORG_ASSIGNMENT).value(), managerId, teamId, "Engineering Manager", "SOLID", deptHead, "MANAGER");
                managerList.add(new long[]{managerId, teamId});
            }
        }

        // 5. Assign remaining users as ICs across teams
        String[] icTitles = {"Software Engineer", "Senior Software Engineer", "Staff Engineer", "Principal Engineer",
                "Product Manager", "Designer", "Senior Designer", "Data Analyst", "QA Engineer",
                "Technical Writer", "Scrum Master", "DevOps Engineer", "SRE"};
        String[] icLevels = {"JUNIOR", "MID", "SENIOR", "LEAD", "SENIOR", "MID"};

        while (idx < userIds.size() && !managerList.isEmpty()) {
            long userId = userIds.get(idx++).value();
            long[] team = managerList.get(random.nextInt(managerList.size()));
            String title = icTitles[random.nextInt(icTitles.length)];
            String level = icLevels[random.nextInt(icLevels.length)];
            insertAssignment(idGen.next(ObjectType.ORG_ASSIGNMENT).value(), userId, team[1], title, "SOLID", team[0], level);

            // 10% chance of dotted-line to another team
            if (random.nextInt(10) == 0 && managerList.size() > 1) {
                long[] otherTeam = managerList.get(random.nextInt(managerList.size()));
                if (otherTeam[1] != team[1]) {
                    try {
                        insertAssignment(idGen.next(ObjectType.ORG_ASSIGNMENT).value(), userId, otherTeam[1], title + " (Advisor)", "DOTTED", otherTeam[0], level);
                    } catch (Exception ignored) {} // unique constraint
                }
            }
        }

        log.info("  Org structure generated: {} departments, {} teams, {} users assigned",
                deptList.size(), managerList.size(), idx);
    }

    private void insertOrgUnit(long id, String name, String type, Long parentId, Long headUserId, String description, String costCenter) {
        jdbc.update("INSERT INTO org_units (id, name, type, parent_id, head_user_id, description, cost_center) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, name, type, parentId, headUserId, description, costCenter);
    }

    private void insertAssignment(long id, long userId, long orgUnitId, String title, String relType, Long reportsTo, String level) {
        jdbc.update("INSERT INTO org_assignments (id, user_id, org_unit_id, title, relationship_type, reports_to_user_id, level) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, userId, orgUnitId, title, relType, reportsTo, level);
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
