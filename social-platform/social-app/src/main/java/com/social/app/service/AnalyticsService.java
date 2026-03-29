package com.social.app.service;

import com.logging.generated.FeedImpressionLogger;
import com.logging.generated.UserInteractionLogger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private FeedImpressionLogger feedImpressionLogger;
    private UserInteractionLogger interactionLogger;
    private boolean enabled = false;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaServers;

    @PostConstruct
    public void init() {
        try {
            feedImpressionLogger = new FeedImpressionLogger(kafkaServers);
            interactionLogger = new UserInteractionLogger(kafkaServers);
            enabled = true;
            log.info("Analytics loggers initialized with Kafka at {}", kafkaServers);
        } catch (Exception e) {
            log.warn("Analytics loggers failed to initialize: {}. Analytics disabled.", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        try { if (feedImpressionLogger != null) feedImpressionLogger.close(); } catch (Exception ignored) {}
        try { if (interactionLogger != null) interactionLogger.close(); } catch (Exception ignored) {}
    }

    /**
     * Log a feed impression with all ranking features.
     */
    public void logFeedImpression(long userId, long postId, long authorId, int position,
            double score, String source, String targetType, Long targetId,
            FeedFeatures features) {
        if (!enabled) return;
        try {
            feedImpressionLogger.log(
                Instant.now(), LocalDate.now(), userId, postId, authorId, position,
                score, source, targetType, targetId,
                features != null ? features.engagement : null,
                features != null ? features.recencyHours : null,
                features != null ? features.affinity : null,
                features != null ? features.reactionCount : null,
                features != null ? features.commentCount : null,
                features != null ? features.authorFollowerCount : null,
                features != null ? features.isRecommended : null,
                features != null ? features.hasAttachment : null,
                features != null ? features.hasPoll : null,
                features != null ? features.socialDistance : null
            );
        } catch (Exception e) {
            log.debug("Failed to log feed impression: {}", e.getMessage());
        }
    }

    /**
     * Log a generic user interaction.
     */
    public void logInteraction(String type, long userId, Long targetId, String targetType,
            Map<String, Object> extra) {
        if (!enabled) return;
        try {
            interactionLogger.log(
                Instant.now(), LocalDate.now(), userId, type,
                targetId, targetType,
                null, // contentAuthorId
                null, // reactionType
                null, // searchQuery
                null, // searchResultCount
                null, // messageHasAttachment
                null, // botContext
                null, // botToolsUsed
                null, // botResponseTimeMs
                null, // groupId
                null, // pageId
                null, // deviceType
                null  // properties
            );
        } catch (Exception e) {
            log.debug("Failed to log interaction: {}", e.getMessage());
        }
    }

    /**
     * Log a reaction on a post.
     */
    public void logReaction(long userId, long postId, long authorId, String reactionType) {
        if (!enabled) return;
        try {
            interactionLogger.log(
                Instant.now(), LocalDate.now(), userId, "reaction",
                postId, "post",
                authorId, reactionType,
                null, // searchQuery
                null, // searchResultCount
                null, // messageHasAttachment
                null, // botContext
                null, // botToolsUsed
                null, // botResponseTimeMs
                null, // groupId
                null, // pageId
                null, // deviceType
                null  // properties
            );
        } catch (Exception e) {
            log.debug("Failed to log reaction: {}", e.getMessage());
        }
    }

    /**
     * Log a comment on a post.
     */
    public void logComment(long userId, long postId, long authorId) {
        if (!enabled) return;
        try {
            interactionLogger.log(
                Instant.now(), LocalDate.now(), userId, "comment",
                postId, "post",
                authorId, null,
                null, // searchQuery
                null, // searchResultCount
                null, // messageHasAttachment
                null, // botContext
                null, // botToolsUsed
                null, // botResponseTimeMs
                null, // groupId
                null, // pageId
                null, // deviceType
                null  // properties
            );
        } catch (Exception e) {
            log.debug("Failed to log comment: {}", e.getMessage());
        }
    }

    /**
     * Log a message sent in a conversation.
     */
    public void logMessageSent(long userId, long conversationId, boolean hasAttachment) {
        if (!enabled) return;
        try {
            interactionLogger.log(
                Instant.now(), LocalDate.now(), userId, "message_sent",
                conversationId, "conversation",
                null, // contentAuthorId
                null, // reactionType
                null, // searchQuery
                null, // searchResultCount
                hasAttachment, // messageHasAttachment
                null, // botContext
                null, // botToolsUsed
                null, // botResponseTimeMs
                null, // groupId
                null, // pageId
                null, // deviceType
                null  // properties
            );
        } catch (Exception e) {
            log.debug("Failed to log message sent: {}", e.getMessage());
        }
    }

    /**
     * Log a search performed by a user.
     */
    public void logSearch(long userId, String query, String type, int resultCount) {
        if (!enabled) return;
        try {
            interactionLogger.log(
                Instant.now(), LocalDate.now(), userId, "search",
                null, // targetId
                type, // targetType (search type: users, posts, etc.)
                null, // contentAuthorId
                null, // reactionType
                query, // searchQuery
                resultCount, // searchResultCount
                null, // messageHasAttachment
                null, // botContext
                null, // botToolsUsed
                null, // botResponseTimeMs
                null, // groupId
                null, // pageId
                null, // deviceType
                null  // properties
            );
        } catch (Exception e) {
            log.debug("Failed to log search: {}", e.getMessage());
        }
    }

    /**
     * Log a profile view.
     */
    public void logProfileView(long userId, long viewedUserId) {
        if (!enabled) return;
        try {
            interactionLogger.log(
                Instant.now(), LocalDate.now(), userId, "profile_view",
                viewedUserId, "user",
                null, // contentAuthorId
                null, // reactionType
                null, // searchQuery
                null, // searchResultCount
                null, // messageHasAttachment
                null, // botContext
                null, // botToolsUsed
                null, // botResponseTimeMs
                null, // groupId
                null, // pageId
                null, // deviceType
                null  // properties
            );
        } catch (Exception e) {
            log.debug("Failed to log profile view: {}", e.getMessage());
        }
    }

    /**
     * Log a bot interaction.
     */
    public void logBotInteraction(long userId, String context, String toolsUsed, long responseTimeMs) {
        if (!enabled) return;
        try {
            interactionLogger.log(
                Instant.now(), LocalDate.now(), userId, "bot_interaction",
                null, // targetId
                "bot", // targetType
                null, // contentAuthorId
                null, // reactionType
                null, // searchQuery
                null, // searchResultCount
                null, // messageHasAttachment
                context, // botContext
                toolsUsed, // botToolsUsed
                responseTimeMs, // botResponseTimeMs
                null, // groupId
                null, // pageId
                null, // deviceType
                null  // properties
            );
        } catch (Exception e) {
            log.debug("Failed to log bot interaction: {}", e.getMessage());
        }
    }

    /**
     * Log a poll vote.
     */
    public void logPollVote(long userId, long pollId, long postId) {
        if (!enabled) return;
        try {
            interactionLogger.log(
                Instant.now(), LocalDate.now(), userId, "poll_vote",
                pollId, "poll",
                null, // contentAuthorId
                null, // reactionType
                null, // searchQuery
                null, // searchResultCount
                null, // messageHasAttachment
                null, // botContext
                null, // botToolsUsed
                null, // botResponseTimeMs
                null, // groupId
                null, // pageId
                null, // deviceType
                null  // properties
            );
        } catch (Exception e) {
            log.debug("Failed to log poll vote: {}", e.getMessage());
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
