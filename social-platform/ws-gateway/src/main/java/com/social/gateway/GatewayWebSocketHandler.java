package com.social.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

/**
 * Non-blocking WebSocket handler using Netty's event loop.
 *
 * Protocol (JSON over WebSocket):
 *
 * Client → Gateway:
 *   {"type":"SUBSCRIBE","conversationId":123}
 *   {"type":"SEND","conversationId":123,"content":"Hello!"}
 *   {"type":"TYPING","conversationId":123}
 *
 * Gateway → Client:
 *   {"type":"MESSAGE","conversationId":123,"data":{...messageDto...}}
 *   {"type":"TYPING","conversationId":123,"userId":456}
 *   {"type":"ERROR","message":"..."}
 *   {"type":"CONNECTED","userId":123,"connections":42}
 */
@Component
public class GatewayWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayWebSocketHandler.class);

    private final ConnectionRegistry registry;
    private final RedisMessageRelay redisRelay;
    private final JwtService jwtService;
    private final WebClient socialAppClient;
    private final ObjectMapper objectMapper;
    private final int maxConnections;

    public GatewayWebSocketHandler(ConnectionRegistry registry,
                                    RedisMessageRelay redisRelay,
                                    JwtService jwtService,
                                    @Value("${gateway.social-app.url}") String socialAppUrl,
                                    @Value("${gateway.max-connections:100000}") int maxConnections) {
        this.registry = registry;
        this.redisRelay = redisRelay;
        this.jwtService = jwtService;
        this.socialAppClient = WebClient.builder().baseUrl(socialAppUrl).build();
        this.objectMapper = new ObjectMapper();
        this.maxConnections = maxConnections;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Extract auth from query params: /ws?token=JWT or /ws?userId=DEBUG_ID
        Long userId = extractUserId(session);
        if (userId == null) {
            return session.send(Mono.just(session.textMessage(
                    "{\"type\":\"ERROR\",\"message\":\"Authentication required. Pass ?token=JWT or ?userId=ID\"}"
            ))).then(session.close());
        }

        // Enforce connection limit
        if (registry.getTotalConnections() >= maxConnections) {
            return session.send(Mono.just(session.textMessage(
                    "{\"type\":\"ERROR\",\"message\":\"Server at capacity\"}"
            ))).then(session.close());
        }

        registry.register(session, userId);

        // Send connected confirmation
        String connectedMsg = String.format(
                "{\"type\":\"CONNECTED\",\"userId\":%d,\"connections\":%d}",
                userId, registry.getTotalConnections());

        Mono<Void> sendConnected = session.send(Mono.just(session.textMessage(connectedMsg)));

        // Auto-subscribe to all user's conversations
        Mono<Void> autoSubscribe = socialAppClient.get()
                .uri("/api/conversations")
                .header("X-Debug-User-Id", String.valueOf(userId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnNext(convList -> {
                    if (convList.isArray()) {
                        for (JsonNode conv : convList) {
                            long convId = conv.get("id").asLong();
                            registry.subscribeToConversation(session, convId);
                        }
                        log.debug("Auto-subscribed user {} to {} conversations", userId, convList.size());
                    }
                })
                .onErrorResume(e -> {
                    log.debug("Failed to auto-subscribe conversations for user {}: {}", userId, e.getMessage());
                    return Mono.empty();
                })
                .then();

        // Handle incoming messages
        Mono<Void> receiveMessages = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> handleClientMessage(session, userId, payload))
                .then();

        return sendConnected.then(autoSubscribe).then(receiveMessages)
                .doFinally(sig -> registry.unregister(session));
    }

    private Mono<Void> handleClientMessage(WebSocketSession session, long userId, String payload) {
        try {
            JsonNode msg = objectMapper.readTree(payload);
            String type = msg.has("type") ? msg.get("type").asText() : "";

            return switch (type) {
                case "SUBSCRIBE" -> handleSubscribe(session, msg);
                case "SEND" -> handleSend(session, userId, msg);
                case "TYPING" -> handleTyping(session, userId, msg);
                default -> sendError(session, "Unknown message type: " + type);
            };
        } catch (Exception e) {
            return sendError(session, "Invalid message format: " + e.getMessage());
        }
    }

    private Mono<Void> handleSubscribe(WebSocketSession session, JsonNode msg) {
        long conversationId = msg.get("conversationId").asLong();
        registry.subscribeToConversation(session, conversationId);
        return Mono.empty();
    }

    private Mono<Void> handleSend(WebSocketSession session, long userId, JsonNode msg) {
        long conversationId = msg.get("conversationId").asLong();
        String content = msg.has("content") ? msg.get("content").asText() : "";

        // Delegate to social app REST API for persistence + business logic
        return socialAppClient.post()
                .uri("/api/conversations/{id}/messages", conversationId)
                .header("X-Debug-User-Id", String.valueOf(userId))
                .bodyValue(new SendMessageRequest(content))
                .retrieve()
                .bodyToMono(String.class)
                .then(Mono.<Void>empty())
                .onErrorResume(e -> {
                    log.warn("Failed to send message via social app: {}", e.getMessage());
                    return sendError(session, "Failed to send message");
                });
    }

    private Mono<Void> handleTyping(WebSocketSession session, long userId, JsonNode msg) {
        long conversationId = msg.get("conversationId").asLong();

        // Broadcast typing indicator to local subscribers
        String typingMsg = String.format(
                "{\"type\":\"TYPING\",\"conversationId\":%d,\"userId\":%d}",
                conversationId, userId);

        for (WebSocketSession sub : registry.getSessionsForConversation(conversationId)) {
            if (sub.isOpen() && !sub.getId().equals(session.getId())) {
                sub.send(Mono.just(sub.textMessage(typingMsg))).subscribe();
            }
        }

        // Also publish to Redis for other gateway nodes
        redisRelay.publishToRedis(conversationId,
                String.format("{\"type\":\"TYPING\",\"userId\":%d}", userId));

        return Mono.empty();
    }

    private Mono<Void> sendError(WebSocketSession session, String message) {
        String errorMsg = String.format("{\"type\":\"ERROR\",\"message\":\"%s\"}", message.replace("\"", "'"));
        return session.send(Mono.just(session.textMessage(errorMsg)));
    }

    private Long extractUserId(WebSocketSession session) {
        String query = session.getHandshakeInfo().getUri().getQuery();
        if (query == null) return null;

        // Try JWT token first
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                String token = param.substring(6);
                return jwtService.extractUserId(token);
            }
            // Debug mode: direct user ID
            if (param.startsWith("userId=")) {
                try {
                    return Long.parseLong(param.substring(7));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    record SendMessageRequest(String content) {}
}
