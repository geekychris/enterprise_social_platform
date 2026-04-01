package com.social.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.social.app.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes analytics events (feed impressions, user interactions) to Kafka.
 *
 * Uses KafkaTemplate directly (same approach as EntityEventService) instead of
 * code-generated loggers so that tenant_id can be included in every event for
 * multi-tenant warehouse isolation.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private static final String FEED_IMPRESSION_TOPIC = "worksphere-feed-impressions";
    private static final String USER_INTERACTION_TOPIC = "worksphere-user-interactions";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AnalyticsService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
        log.info("AnalyticsService initialized with direct KafkaTemplate publishing");
    }

    /**
     * Log a feed impression with all ranking features.
     */
    public void logFeedImpression(long userId, long postId, long authorId, int position,
            double score, String source, String targetType, Long targetId,
            FeedFeatures features) {
        try {
            Instant now = Instant.now();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("timestamp", now.toString());
            data.put("event_date", LocalDate.now(ZoneOffset.UTC).toString());
            data.put("tenant_id", TenantContext.getTenantId());
            data.put("user_id", userId);
            data.put("post_id", postId);
            data.put("author_id", authorId);
            data.put("position", position);
            data.put("score", score);
            data.put("source", source);
            data.put("target_type", targetType);
            data.put("target_id", targetId);
            data.put("feat_engagement", features != null ? features.engagement : null);
            data.put("feat_recency_hours", features != null ? features.recencyHours : null);
            data.put("feat_affinity", features != null ? features.affinity : null);
            data.put("feat_reaction_count", features != null ? features.reactionCount : null);
            data.put("feat_comment_count", features != null ? features.commentCount : null);
            data.put("feat_author_follower_count", features != null ? features.authorFollowerCount : null);
            data.put("feat_is_recommended", features != null ? features.isRecommended : null);
            data.put("feat_has_attachment", features != null ? features.hasAttachment : null);
            data.put("feat_has_poll", features != null ? features.hasPoll : null);
            data.put("feat_social_distance", features != null ? features.socialDistance : null);

            publishEnvelope(FEED_IMPRESSION_TOPIC, "feed_impression", "FeedImpression",
                    String.valueOf(userId), data);
        } catch (Exception e) {
            log.debug("Failed to log feed impression: {}", e.getMessage());
        }
    }

    /**
     * Log a generic user interaction.
     */
    public void logInteraction(String type, long userId, Long targetId, String targetType,
            Map<String, Object> extra) {
        try {
            Map<String, Object> data = buildInteractionData(userId, type, targetId, targetType,
                    null, null, null, null, null, null, null, null, null, null);
            publishEnvelope(USER_INTERACTION_TOPIC, "user_interaction", "UserInteraction",
                    String.valueOf(userId), data);
        } catch (Exception e) {
            log.debug("Failed to log interaction: {}", e.getMessage());
        }
    }

    /**
     * Log a reaction on a post.
     */
    public void logReaction(long userId, long postId, long authorId, String reactionType) {
        try {
            Map<String, Object> data = buildInteractionData(userId, "reaction", postId, "post",
                    authorId, reactionType, null, null, null, null, null, null, null, null);
            publishEnvelope(USER_INTERACTION_TOPIC, "user_interaction", "UserInteraction",
                    String.valueOf(userId), data);
        } catch (Exception e) {
            log.debug("Failed to log reaction: {}", e.getMessage());
        }
    }

    /**
     * Log a comment on a post.
     */
    public void logComment(long userId, long postId, long authorId) {
        try {
            Map<String, Object> data = buildInteractionData(userId, "comment", postId, "post",
                    authorId, null, null, null, null, null, null, null, null, null);
            publishEnvelope(USER_INTERACTION_TOPIC, "user_interaction", "UserInteraction",
                    String.valueOf(userId), data);
        } catch (Exception e) {
            log.debug("Failed to log comment: {}", e.getMessage());
        }
    }

    /**
     * Log a message sent in a conversation.
     */
    public void logMessageSent(long userId, long conversationId, boolean hasAttachment) {
        try {
            Map<String, Object> data = buildInteractionData(userId, "message_sent", conversationId,
                    "conversation", null, null, null, null, hasAttachment, null, null, null, null, null);
            publishEnvelope(USER_INTERACTION_TOPIC, "user_interaction", "UserInteraction",
                    String.valueOf(userId), data);
        } catch (Exception e) {
            log.debug("Failed to log message sent: {}", e.getMessage());
        }
    }

    /**
     * Log a search performed by a user.
     */
    public void logSearch(long userId, String query, String type, int resultCount) {
        try {
            Map<String, Object> data = buildInteractionData(userId, "search", null, type,
                    null, null, query, resultCount, null, null, null, null, null, null);
            publishEnvelope(USER_INTERACTION_TOPIC, "user_interaction", "UserInteraction",
                    String.valueOf(userId), data);
        } catch (Exception e) {
            log.debug("Failed to log search: {}", e.getMessage());
        }
    }

    /**
     * Log a profile view.
     */
    public void logProfileView(long userId, long viewedUserId) {
        try {
            Map<String, Object> data = buildInteractionData(userId, "profile_view", viewedUserId,
                    "user", null, null, null, null, null, null, null, null, null, null);
            publishEnvelope(USER_INTERACTION_TOPIC, "user_interaction", "UserInteraction",
                    String.valueOf(userId), data);
        } catch (Exception e) {
            log.debug("Failed to log profile view: {}", e.getMessage());
        }
    }

    /**
     * Log a bot interaction.
     */
    public void logBotInteraction(long userId, String context, String toolsUsed, long responseTimeMs) {
        try {
            Map<String, Object> data = buildInteractionData(userId, "bot_interaction", null, "bot",
                    null, null, null, null, null, context, toolsUsed, responseTimeMs, null, null);
            publishEnvelope(USER_INTERACTION_TOPIC, "user_interaction", "UserInteraction",
                    String.valueOf(userId), data);
        } catch (Exception e) {
            log.debug("Failed to log bot interaction: {}", e.getMessage());
        }
    }

    /**
     * Log a poll vote.
     */
    public void logPollVote(long userId, long pollId, long postId) {
        try {
            Map<String, Object> data = buildInteractionData(userId, "poll_vote", pollId, "poll",
                    null, null, null, null, null, null, null, null, null, null);
            publishEnvelope(USER_INTERACTION_TOPIC, "user_interaction", "UserInteraction",
                    String.valueOf(userId), data);
        } catch (Exception e) {
            log.debug("Failed to log poll vote: {}", e.getMessage());
        }
    }

    // -- Internal helpers --

    private Map<String, Object> buildInteractionData(long userId, String interactionType,
            Long targetId, String targetType, Long contentAuthorId, String reactionType,
            String searchQuery, Integer searchResultCount, Boolean messageHasAttachment,
            String botContext, String botToolsUsed, Long botResponseTimeMs,
            Long groupId, Long pageId) {
        Instant now = Instant.now();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("timestamp", now.toString());
        data.put("event_date", LocalDate.now(ZoneOffset.UTC).toString());
        data.put("tenant_id", TenantContext.getTenantId());
        data.put("user_id", userId);
        data.put("interaction_type", interactionType);
        data.put("target_id", targetId);
        data.put("target_type", targetType);
        data.put("content_author_id", contentAuthorId);
        data.put("reaction_type", reactionType);
        data.put("search_query", searchQuery);
        data.put("search_result_count", searchResultCount);
        data.put("message_has_attachment", messageHasAttachment);
        data.put("bot_context", botContext);
        data.put("bot_tools_used", botToolsUsed);
        data.put("bot_response_time_ms", botResponseTimeMs);
        data.put("group_id", groupId);
        data.put("page_id", pageId);
        data.put("device_type", null);
        data.put("properties", null);
        return data;
    }

    private void publishEnvelope(String topic, String logType, String logClass,
            String key, Map<String, Object> data) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("_log_type", logType);
            envelope.put("_log_class", logClass);
            envelope.put("_version", "1.0.0");
            envelope.put("data", data);

            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, key, json);
        } catch (Exception e) {
            log.debug("Failed to publish analytics event to {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Feed ranking feature container.
     */
    public static class FeedFeatures {
        public Double engagement;
        public Double recencyHours;
        public Double affinity;
        public Integer reactionCount;
        public Integer commentCount;
        public Integer authorFollowerCount;
        public Boolean isRecommended;
        public Boolean hasAttachment;
        public Boolean hasPoll;
        public Integer socialDistance;
    }
}
