package com.social.datagen.generator;

import com.social.core.id.GlobalId;
import com.social.core.id.GlobalIdGenerator;
import com.social.core.id.ObjectType;
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
 * Creates attachment records on ~20% of posts, referencing placeholder URLs.
 */
public class AttachmentGenerator {

    private static final Logger log = LoggerFactory.getLogger(AttachmentGenerator.class);

    private static final String INSERT_ATTACHMENT_SQL =
            "INSERT INTO attachments (id, owner_id, media_type, file_url, file_size, " +
            "width, height, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String[] IMAGE_DIMENSIONS = {
            "800/600", "1200/800", "1024/768", "640/480", "1920/1080"
    };

    private static final String[] FILE_EXTENSIONS = {
            "png", "jpg", "jpeg", "gif", "webp"
    };

    private static final String[] FILE_NAME_PREFIXES = {
            "screenshot", "diagram", "photo", "mockup", "chart",
            "whiteboard", "architecture", "wireframe", "dashboard", "report",
            "presentation_slide", "team_photo", "product_demo", "metrics",
            "design_review", "flow_chart", "org_chart", "roadmap"
    };

    private static final String[] VIDEO_NAMES = {
            "demo_recording.mp4", "meeting_recap.mp4", "tutorial.mp4",
            "product_walkthrough.mp4", "keynote_recording.mp4"
    };

    private final JdbcTemplate jdbc;
    private final GlobalIdGenerator idGen;
    private final Random random;
    private final int batchSize;

    public AttachmentGenerator(JdbcTemplate jdbc, GlobalIdGenerator idGen,
                               Random random, int batchSize) {
        this.jdbc = jdbc;
        this.idGen = idGen;
        this.random = random;
        this.batchSize = batchSize;
    }

    /**
     * Generate attachments on ~20% of posts.
     */
    public void generate(int count, List<GlobalId> postIds) {
        log.info("Generating {} attachments...", count);

        // Pick ~20% of posts to have attachments
        List<GlobalId> eligiblePosts = new ArrayList<>();
        for (GlobalId postId : postIds) {
            if (random.nextDouble() < 0.20) {
                eligiblePosts.add(postId);
            }
        }

        jdbc.execute(INSERT_ATTACHMENT_SQL, (PreparedStatement ps) -> {
            int batchCount = 0;
            int generated = 0;
            Instant now = Instant.now();

            while (generated < count) {
                GlobalId attachmentId = idGen.next(ObjectType.ATTACHMENT);

                // Pick a post (cycle through eligible posts, some may get multiple attachments)
                GlobalId postId = eligiblePosts.isEmpty()
                        ? postIds.get(random.nextInt(postIds.size()))
                        : eligiblePosts.get(generated % eligiblePosts.size());

                boolean isVideo = random.nextDouble() < 0.05; // 5% videos
                String fileUrl;
                String mediaType;
                long fileSize;
                int width;
                int height;

                if (isVideo) {
                    fileUrl = "https://placeholder.video/640x360";
                    mediaType = "video/mp4";
                    fileSize = 10_000_000L + random.nextInt(90_000_000); // 10-100MB
                    width = 640;
                    height = 360;
                } else {
                    String ext = FILE_EXTENSIONS[random.nextInt(FILE_EXTENSIONS.length)];

                    String dimensions = IMAGE_DIMENSIONS[random.nextInt(IMAGE_DIMENSIONS.length)];
                    String[] dims = dimensions.split("/");
                    width = Integer.parseInt(dims[0]);
                    height = Integer.parseInt(dims[1]);
                    int seed = random.nextInt(10000);
                    fileUrl = "https://picsum.photos/seed/" + seed + "/" + dimensions;
                    mediaType = "image/" + ext;
                    fileSize = 50_000L + random.nextInt(5_000_000); // 50KB-5MB
                }

                Timestamp createdAt = Timestamp.from(
                        now.minus((long) (random.nextDouble() * 4000), ChronoUnit.HOURS));

                ps.setLong(1, attachmentId.value());
                ps.setLong(2, postId.value());
                ps.setString(3, mediaType);
                ps.setString(4, fileUrl);
                ps.setLong(5, fileSize);
                ps.setInt(6, width);
                ps.setInt(7, height);
                ps.setTimestamp(8, createdAt);
                ps.addBatch();
                batchCount++;
                generated++;

                if (batchCount >= batchSize) {
                    ps.executeBatch();
                    batchCount = 0;
                }

                if (generated % 1000 == 0 || generated == count) {
                    log.info("  Attachments generated: {}/{}", generated, count);
                }
            }

            if (batchCount > 0) {
                ps.executeBatch();
            }
            return null;
        });
    }
}
