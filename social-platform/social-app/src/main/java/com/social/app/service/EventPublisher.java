package com.social.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class EventPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    public EventPublisher(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Published after a message is sent.
     */
    public void publishMessageSent(long conversationId, long senderId, long messageId, String content) {
        Map<String, Object> event = Map.of(
                "type", "MESSAGE_SENT",
                "conversationId", conversationId,
                "senderId", senderId,
                "messageId", messageId,
                "content", content != null ? content : "",
                "timestamp", Instant.now().toString()
        );
        try { kafka.send("messages.sent", String.valueOf(conversationId), objectMapper.writeValueAsString(event)); } catch (Exception ignored) {}
    }

    /**
     * Published after a post is created.
     */
    public void publishPostCreated(long postId, long authorId, String targetType, Long targetId) {
        Map<String, Object> event = Map.of(
                "type", "POST_CREATED",
                "postId", postId,
                "authorId", authorId,
                "targetType", targetType != null ? targetType : "USER_FEED",
                "targetId", targetId != null ? targetId : 0L,
                "timestamp", Instant.now().toString()
        );
        try { kafka.send("posts.created", String.valueOf(postId), objectMapper.writeValueAsString(event)); } catch (Exception ignored) {}
    }

    /**
     * Published after a reaction is added.
     */
    public void publishReactionAdded(long targetId, long userId, String reactionType) {
        try {
            kafka.send("reactions.added", String.valueOf(targetId),
                    objectMapper.writeValueAsString(Map.of("targetId", targetId, "userId", userId, "type", reactionType)));
        } catch (Exception ignored) {}
    }
}
