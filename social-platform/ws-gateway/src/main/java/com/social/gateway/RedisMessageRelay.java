package com.social.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;

import java.util.Set;

/**
 * Subscribes to Redis Pub/Sub channels for cross-node message relay.
 * When a message is published from any app node (via MessageBroadcastService),
 * this gateway picks it up and pushes to locally-connected WebSocket clients.
 */
@Service
public class RedisMessageRelay {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageRelay.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ConnectionRegistry registry;
    private final ObjectMapper objectMapper;
    private Disposable subscription;

    public RedisMessageRelay(ReactiveStringRedisTemplate redisTemplate,
                              ConnectionRegistry registry) {
        this.redisTemplate = redisTemplate;
        this.registry = registry;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void start() {
        // Subscribe to all conversation channels: conversation:*
        subscription = redisTemplate.listenTo(new PatternTopic("conversation:*"))
                .doOnNext(this::handleRedisMessage)
                .doOnError(e -> log.error("Redis subscription error: {}", e.getMessage()))
                .subscribe();

        log.info("Redis message relay started (pattern: conversation:*)");
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) subscription.dispose();
    }

    private void handleRedisMessage(ReactiveSubscription.Message<String, String> message) {
        try {
            String channel = message.getChannel();
            String payload = message.getMessage();

            // Extract conversation ID from channel: "conversation:12345"
            long conversationId = Long.parseLong(channel.substring(channel.indexOf(':') + 1));

            // Push to all locally-connected subscribers for this conversation
            Set<WebSocketSession> sessions = registry.getSessionsForConversation(conversationId);
            if (sessions.isEmpty()) return;

            // Wrap in a protocol message
            String wsMessage = "{\"type\":\"MESSAGE\",\"conversationId\":" + conversationId
                    + ",\"data\":" + payload + "}";

            for (WebSocketSession session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.send(reactor.core.publisher.Mono.just(
                                session.textMessage(wsMessage)
                        )).subscribe();
                    }
                } catch (Exception e) {
                    log.debug("Failed to push to session {}: {}", session.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to handle Redis message: {}", e.getMessage());
        }
    }

    /**
     * Publish a message to Redis so other gateway nodes can relay it.
     */
    public void publishToRedis(long conversationId, String messageJson) {
        redisTemplate.convertAndSend("conversation:" + conversationId, messageJson)
                .subscribe();
    }
}
