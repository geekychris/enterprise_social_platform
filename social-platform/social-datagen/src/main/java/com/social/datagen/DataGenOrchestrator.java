package com.social.datagen;

import com.social.core.id.GlobalId;
import com.social.core.id.GlobalIdGenerator;
import com.social.datagen.generator.*;
import com.social.datagen.generator.ContentGenerator.AuthorshipEdge;
import com.social.datagen.generator.CommentGenerator.CommentEdge;
import com.social.datagen.generator.ReactionGenerator.ReactionEdge;
import com.social.datagen.generator.SocialGraphGenerator.FollowEdge;
import com.social.datagen.generator.SocialGraphGenerator.MembershipEdge;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Coordinates data generation in correct foreign-key order:
 * Users -> Orgs -> Memberships/Follows -> Posts -> Comments -> Reactions -> Attachments
 */
@Component
public class DataGenOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DataGenOrchestrator.class);

    private final JdbcTemplate jdbc;
    private final AoeeSyncService aoeeSyncService;

    @Value("${datagen.batch-size:500}")
    private int batchSize;

    @Value("${datagen.seed:42}")
    private long seed;

    public DataGenOrchestrator(JdbcTemplate jdbc, AoeeSyncService aoeeSyncService) {
        this.jdbc = jdbc;
        this.aoeeSyncService = aoeeSyncService;
    }

    public void generate(DataGenConfig.Mode mode, boolean aoeeSync) {
        DataGenConfig config = DataGenConfig.forMode(mode);
        log.info("=== Data Generation Starting ===");
        log.info("Mode: {}", mode);
        log.info("Users: {}, Teams: {}, Groups: {}, Pages: {}, Projects: {}",
                config.users, config.teams, config.groups, config.pages, config.projects);
        log.info("Posts: {}, Comments: {}, Reactions: {}, Attachments: {}",
                config.posts, config.comments, config.reactions, config.attachments);
        log.info("Batch size: {}, Seed: {}", batchSize, seed);

        // Deterministic seeding for reproducible datasets
        Random random = new Random(seed);
        Faker faker = new Faker(new java.util.Locale("en"), random);
        GlobalIdGenerator idGen = new GlobalIdGenerator();

        // Instantiate generators
        UserGenerator userGen = new UserGenerator(jdbc, idGen, faker, random, batchSize);
        OrgGenerator orgGen = new OrgGenerator(jdbc, idGen, faker, random, batchSize);
        SocialGraphGenerator socialGen = new SocialGraphGenerator(jdbc, random, batchSize);
        ContentGenerator contentGen = new ContentGenerator(jdbc, idGen, faker, random, batchSize);
        CommentGenerator commentGen = new CommentGenerator(jdbc, idGen, faker, random, batchSize);
        ReactionGenerator reactionGen = new ReactionGenerator(jdbc, idGen, random, batchSize);
        AttachmentGenerator attachmentGen = new AttachmentGenerator(jdbc, idGen, random, batchSize);

        // ---- Phase 1: Users ----
        log.info("");
        log.info("--- Phase 1: Users ---");
        List<GlobalId> userIds = userGen.generate(config.users);

        // ---- Phase 2: Organizations ----
        log.info("");
        log.info("--- Phase 2: Teams, Groups, Pages, Projects ---");
        List<GlobalId> teamIds = orgGen.generateTeams(config.teams);
        List<GlobalId> groupIds = orgGen.generateGroups(config.groups);
        List<GlobalId> pageIds = orgGen.generatePages(config.pages);
        List<GlobalId> projectIds = orgGen.generateProjects(config.projects, pageIds);

        // ---- Phase 2b: Org Structure ----
        log.info("");
        log.info("--- Phase 2b: Organizational Structure ---");
        orgGen.generateOrgStructure(userIds);

        // ---- Phase 3: Social Graph ----
        log.info("");
        log.info("--- Phase 3: Memberships and Follows ---");
        List<MembershipEdge> teamMemberships = socialGen.generateTeamMemberships(userIds, teamIds);
        List<MembershipEdge> groupMemberships = socialGen.generateGroupMemberships(userIds, groupIds);
        List<MembershipEdge> pageFollows = socialGen.generatePageFollows(userIds, pageIds);
        List<FollowEdge> follows = socialGen.generateFollows(userIds);

        // ---- Phase 4: Content ----
        log.info("");
        log.info("--- Phase 4: Posts ---");
        List<AuthorshipEdge> authorshipEdges = new ArrayList<>();
        List<GlobalId> postIds = contentGen.generate(config.posts, userIds, teamIds,
                pageIds, groupIds, projectIds, authorshipEdges);

        // ---- Phase 5: Comments ----
        log.info("");
        log.info("--- Phase 5: Comments ---");
        List<CommentEdge> commentEdges = commentGen.generate(config.comments, postIds, userIds);

        // ---- Phase 6: Reactions ----
        log.info("");
        log.info("--- Phase 6: Reactions ---");
        List<ReactionEdge> reactionEdges = reactionGen.generate(config.reactions, postIds, userIds);

        // ---- Phase 7: Attachments ----
        log.info("");
        log.info("--- Phase 7: Attachments ---");
        attachmentGen.generate(config.attachments, postIds);

        // ---- Summary ----
        log.info("");
        log.info("=== Data Generation Summary ===");
        log.info("Users: {}", userIds.size());
        log.info("Teams: {}, Groups: {}, Pages: {}, Projects: {}",
                teamIds.size(), groupIds.size(), pageIds.size(), projectIds.size());
        log.info("User follows: {}", follows.size());
        log.info("Team memberships: {}, Group memberships: {}, Page follows: {}",
                teamMemberships.size(), groupMemberships.size(), pageFollows.size());
        log.info("Posts: {}, Comments: {}, Reactions: {}, Attachments: {}",
                postIds.size(), commentEdges.size(), reactionEdges.size(), config.attachments);

        // ---- AOEE Sync ----
        if (aoeeSync) {
            log.info("");
            log.info("--- AOEE Sync ---");
            aoeeSyncService.syncAll(follows, teamMemberships, groupMemberships,
                    pageFollows, authorshipEdges, commentEdges, reactionEdges);
        } else {
            log.info("AOEE sync skipped (use --aoee-sync=true to enable)");
        }

        log.info("");
        log.info("=== Data Generation Complete ===");
    }
}
