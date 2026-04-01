package com.social.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.social.app.tenant.TenantContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes entity CDC (Change Data Capture) events to Kafka for warehouse ingestion.
 * Each event contains the full entity state at the time of the change.
 * Events are consumed by the same Spark consumer that handles structured logging
 * (feed impressions, user interactions) and written to hourly Iceberg tables.
 *
 * NOTE: This class publishes directly via KafkaTemplate rather than using code-generated
 * loggers from the structured-logger framework (like FeedImpressionLogger). The wire format
 * is identical — same JSON envelope ({_log_type, _log_class, _version, data: {...}}) — so
 * the Spark consumer processes these identically. To be fully consistent, these should be
 * replaced with generated loggers from the log-configs/entity_*.json schemas by running:
 *   python3 lib/structured-logger/generators/generate_loggers.py log-configs/entity_user.json --lang java
 * and wiring the generated EntityUserLogger, EntityPostLogger, etc. into AnalyticsService.
 */
@Service
public class EntityEventService {

    private static final Logger log = LoggerFactory.getLogger(EntityEventService.class);
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private boolean enabled = true;

    public EntityEventService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // for Java time
    }

    // -- User Events --

    public void publishUserEvent(String eventType, long id, String username, String displayName,
                                  String email, String avatarUrl, String bio, String visibility,
                                  boolean isAdmin, String jobTitle, String department,
                                  Long managerId, String location, boolean isBot, Instant createdAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("username", username);
        data.put("display_name", displayName);
        data.put("email", email);
        data.put("avatar_url", avatarUrl);
        data.put("bio", bio);
        data.put("visibility", visibility);
        data.put("is_admin", isAdmin);
        data.put("job_title", jobTitle);
        data.put("department", department);
        data.put("manager_id", managerId);
        data.put("location", location);
        data.put("is_bot", isBot);
        data.put("created_at", createdAt != null ? createdAt.toString() : null);
        publish("worksphere-entity-users", "entity_user", "EntityUser", eventType, String.valueOf(id), data);
    }

    // -- Post Events --

    public void publishPostEvent(String eventType, long id, long authorId, String content,
                                  String visibility, String targetType, Long targetId, Instant createdAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("author_id", authorId);
        data.put("content", content);
        data.put("visibility", visibility);
        data.put("target_type", targetType);
        data.put("target_id", targetId);
        data.put("created_at", createdAt != null ? createdAt.toString() : null);
        publish("worksphere-entity-posts", "entity_post", "EntityPost", eventType, String.valueOf(id), data);
    }

    // -- Comment Events --

    public void publishCommentEvent(String eventType, long id, long postId, long authorId,
                                     String content, Long parentId, int depth, Instant createdAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("post_id", postId);
        data.put("author_id", authorId);
        data.put("content", content);
        data.put("parent_id", parentId);
        data.put("depth", depth);
        data.put("created_at", createdAt != null ? createdAt.toString() : null);
        publish("worksphere-entity-comments", "entity_comment", "EntityComment", eventType, String.valueOf(id), data);
    }

    // -- Reaction Events --

    public void publishReactionEvent(String eventType, long id, long userId, long targetId,
                                      String targetType, String reactionType, Instant createdAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("user_id", userId);
        data.put("target_id", targetId);
        data.put("target_type", targetType);
        data.put("reaction_type", reactionType);
        data.put("created_at", createdAt != null ? createdAt.toString() : null);
        publish("worksphere-entity-reactions", "entity_reaction", "EntityReaction", eventType, String.valueOf(id), data);
    }

    // -- Message Events --

    public void publishMessageEvent(String eventType, long id, long conversationId, long senderId,
                                     int contentLength, boolean hasAttachment, Instant createdAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("conversation_id", conversationId);
        data.put("sender_id", senderId);
        data.put("content_length", contentLength);
        data.put("has_attachment", hasAttachment);
        data.put("created_at", createdAt != null ? createdAt.toString() : null);
        publish("worksphere-entity-messages", "entity_message", "EntityMessage", eventType, String.valueOf(id), data);
    }

    // -- Group Events --

    public void publishGroupEvent(String eventType, long id, String name, String description,
                                   String visibility, Long ownerId, Instant createdAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("name", name);
        data.put("description", description);
        data.put("visibility", visibility);
        data.put("owner_id", ownerId);
        data.put("created_at", createdAt != null ? createdAt.toString() : null);
        publish("worksphere-entity-groups", "entity_group", "EntityGroup", eventType, String.valueOf(id), data);
    }

    // -- Page Events --

    public void publishPageEvent(String eventType, long id, String name, String description,
                                  String visibility, Long ownerId, Instant createdAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("name", name);
        data.put("description", description);
        data.put("visibility", visibility);
        data.put("owner_id", ownerId);
        data.put("created_at", createdAt != null ? createdAt.toString() : null);
        publish("worksphere-entity-pages", "entity_page", "EntityPage", eventType, String.valueOf(id), data);
    }

    // -- Follow Events --

    public void publishFollowEvent(String eventType, long followerId, long followedId, Instant createdAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("follower_id", followerId);
        data.put("followed_id", followedId);
        data.put("created_at", createdAt != null ? createdAt.toString() : null);
        publish("worksphere-entity-follows", "entity_follow", "EntityFollow", eventType,
                followerId + "-" + followedId, data);
    }

    // -- Membership Events --

    public void publishMembershipEvent(String eventType, long userId, long groupId,
                                        String role, String status, Instant createdAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId);
        data.put("group_id", groupId);
        data.put("role", role);
        data.put("status", status);
        data.put("created_at", createdAt != null ? createdAt.toString() : null);
        publish("worksphere-entity-memberships", "entity_membership", "EntityMembership", eventType,
                userId + "-" + groupId, data);
    }

    // -- Core publish --

    private void publish(String topic, String logType, String logClass, String eventType,
                         String key, Map<String, Object> entityData) {
        if (!enabled) return;
        try {
            Instant now = Instant.now();
            String eventHour = now.atOffset(ZoneOffset.UTC).format(HOUR_FMT);

            // Add tenant context
            entityData.put("tenant_id", TenantContext.getTenantId());

            // Add event metadata to data
            entityData.put("event_type", eventType);
            entityData.put("event_timestamp", now.toString());
            entityData.put("event_hour", eventHour);
            entityData.put("event_date", LocalDate.now(ZoneOffset.UTC).toString());

            // Wrap in structured logging envelope
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("_log_type", logType);
            envelope.put("_log_class", logClass);
            envelope.put("_version", "1.0.0");
            envelope.put("data", entityData);

            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, key, json);
        } catch (Exception e) {
            log.debug("Failed to publish entity event to {}: {}", topic, e.getMessage());
        }
    }
}
