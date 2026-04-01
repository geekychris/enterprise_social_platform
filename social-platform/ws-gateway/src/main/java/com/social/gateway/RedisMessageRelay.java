package com.social.gateway;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;

import java.util.Set;

/**
 * Subscribes to Redis Pub/Sub pattern conversation:* for cross-node message relay.
 * Uses the Lettuce reactive pub/sub API directly for reliable pattern subscription.
 */
@Service
public class RedisMessageRelay {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageRelay.class);

    private final LettuceConnectionFactory connectionFactory;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ConnectionRegistry registry;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private Disposable subscription;

    public RedisMessageRelay(LettuceConnectionFactory connectionFactory,
                              ReactiveStringRedisTemplate redisTemplate,
                              ConnectionRegistry registry) {
        this.connectionFactory = connectionFactory;
        this.redisTemplate = redisTemplate;
        this.registry = registry;
    }

    @PostConstruct
    public void start() {
        // Get a dedicated pub/sub connection from Lettuce
        var client = (io.lettuce.core.RedisClient) connectionFactory.getNativeClient();
        pubSubConnection = client.connectPubSub();

        // Use reactive API for non-blocking message handling
        RedisPubSubReactiveCommands<String, String> reactive = pubSubConnection.reactive();

        // First subscribe to the pattern, then observe
        reactive.psubscribe("*:conversation:*").doOnSuccess(v ->
            log.info("Redis psubscribe confirmed for *:conversation:*")
        ).subscribe();

        subscription = reactive.observePatterns()
                .doOnNext(message -> {
                    try {
                        String channel = message.getChannel();
                        String payload = message.getMessage();
                        log.info("Redis relay received on channel: {}", channel);

                        // Channel format: {tenantId}:conversation:{conversationId}
                        String[] parts = channel.split(":");
                        // parts[0] = tenantId, parts[1] = "conversation", parts[2] = conversationId
                        long conversationId = Long.parseLong(parts[2]);

                        Set<WebSocketSession> sessions = registry.getSessionsForConversation(conversationId);
                        log.info("Found {} sessions for conversation {}", sessions.size(), conversationId);
                        if (sessions.isEmpty()) return;

                        String wsMessage = "{\"type\":\"MESSAGE\",\"conversationId\":" + conversationId
                                + ",\"data\":" + payload + "}";

                        for (WebSocketSession session : sessions) {
                            try {
                                if (session.isOpen()) {
                                    session.send(reactor.core.publisher.Mono.just(
                                            session.textMessage(wsMessage)
                                    )).subscribe();
                                    log.info("Pushed to session {}", session.getId());
                                }
                            } catch (Exception e) {
                                log.warn("Push failed for session {}: {}", session.getId(), e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to handle Redis message: {}", e.getMessage());
                    }
                })
                .doOnError(e -> log.error("Redis observePatterns error: {}", e.getMessage()))
                .subscribe();

        log.info("Redis message relay started (pSubscribe: *:conversation:*)");
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) subscription.dispose();
        if (pubSubConnection != null) pubSubConnection.close();
    }

    public void publishToRedis(long conversationId, String messageJson) {
        redisTemplate.convertAndSend("conversation:" + conversationId, messageJson)
                .subscribe();
    }
}
