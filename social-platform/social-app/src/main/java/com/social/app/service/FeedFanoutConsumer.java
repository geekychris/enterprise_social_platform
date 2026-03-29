package com.social.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.app.persistence.repository.FollowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.social.app.persistence.repository.MembershipRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class FeedFanoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(FeedFanoutConsumer.class);

    private final JdbcTemplate jdbc;
    private final FollowRepository followRepo;
    private final MembershipRepository membershipRepo;
    private final StringRedisTemplate redis;

    public FeedFanoutConsumer(JdbcTemplate jdbc, FollowRepository followRepo,
                               MembershipRepository membershipRepo, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.followRepo = followRepo;
        this.membershipRepo = membershipRepo;
        this.redis = redis;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "posts.created", groupId = "feed-fanout")
    public void onPostCreated(String message) {
        // Parse event
        Map<String, Object> event;
        try { event = new ObjectMapper().readValue(message, Map.class); }
        catch (Exception e) { log.error("Failed to parse event: {}", e.getMessage()); return; }
        long postId = ((Number) event.get("postId")).longValue();
        long authorId = ((Number) event.get("authorId")).longValue();
        String targetType = (String) event.get("targetType");
        long targetId = ((Number) event.get("targetId")).longValue();

        // Find all users who should see this post
        Set<Long> recipients = new HashSet<>();

        // Author's followers
        followRepo.findByFollowedId(authorId).forEach(f -> recipients.add(f.getFollowerId()));

        // Group/page members
        if ("GROUP_FEED".equals(targetType) && targetId > 0) {
            membershipRepo.findByGroupIdAndStatus(targetId, "APPROVED")
                    .forEach(m -> recipients.add(m.getUserId()));
        }

        recipients.add(authorId); // Author sees own posts

        // Fan out to each user's feed (Redis sorted set + DB)
        double score = System.currentTimeMillis();
        for (Long userId : recipients) {
            // Redis: ZADD feed:{userId} {score} {postId}
            redis.opsForZSet().add("feed:" + userId, String.valueOf(postId), score);
            // Trim to max 500 entries
            redis.opsForZSet().removeRange("feed:" + userId, 0, -501);
        }

        // Also store in DB for persistence
        jdbc.batchUpdate(
                "INSERT INTO feed_entries (user_id, post_id, score, source) " +
                "VALUES (?, ?, ?, 'ORGANIC') ON CONFLICT DO NOTHING",
                recipients.stream().map(uid -> new Object[]{uid, postId, score}).toList()
        );
    }
}
