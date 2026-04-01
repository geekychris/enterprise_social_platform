package com.social.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks all active WebSocket connections on this gateway node.
 * Thread-safe for concurrent connection/disconnection.
 *
 * Two indexes:
 * - userId → set of sessions (a user may have multiple tabs/devices)
 * - conversationId → set of sessions (for targeted broadcast)
 */
@Component
public class ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);

    // userId → active sessions
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    // conversationId → active sessions subscribed to it
    private final Map<Long, Set<WebSocketSession>> conversationSubscribers = new ConcurrentHashMap<>();

    // session → userId (reverse lookup for cleanup)
    private final Map<String, Long> sessionToUser = new ConcurrentHashMap<>();

    // session → tenantId
    private final Map<String, Long> sessionToTenant = new ConcurrentHashMap<>();

    // session → subscribed conversations (for cleanup)
    private final Map<String, Set<Long>> sessionConversations = new ConcurrentHashMap<>();

    private final AtomicInteger totalConnections = new AtomicInteger(0);

    public void register(WebSocketSession session, long userId) {
        register(session, userId, null);
    }

    public void register(WebSocketSession session, long userId, Long tenantId) {
        sessionToUser.put(session.getId(), userId);
        if (tenantId != null) {
            sessionToTenant.put(session.getId(), tenantId);
        }
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionConversations.put(session.getId(), new CopyOnWriteArraySet<>());
        int total = totalConnections.incrementAndGet();
        log.debug("User {} (tenant {}) connected (session {}). Total: {}", userId, tenantId, session.getId(), total);
    }

    public void subscribeToConversation(WebSocketSession session, long conversationId) {
        conversationSubscribers.computeIfAbsent(conversationId, k -> new CopyOnWriteArraySet<>()).add(session);
        Set<Long> convs = sessionConversations.get(session.getId());
        if (convs != null) convs.add(conversationId);
    }

    public void unregister(WebSocketSession session) {
        String sessionId = session.getId();
        Long userId = sessionToUser.remove(sessionId);
        sessionToTenant.remove(sessionId);

        if (userId != null) {
            Set<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) userSessions.remove(userId);
            }
        }

        // Clean up conversation subscriptions
        Set<Long> convs = sessionConversations.remove(sessionId);
        if (convs != null) {
            for (Long convId : convs) {
                Set<WebSocketSession> subs = conversationSubscribers.get(convId);
                if (subs != null) {
                    subs.remove(session);
                    if (subs.isEmpty()) conversationSubscribers.remove(convId);
                }
            }
        }

        int total = totalConnections.decrementAndGet();
        log.debug("User {} disconnected (session {}). Total: {}", userId, sessionId, total);
    }

    public Set<WebSocketSession> getSessionsForUser(long userId) {
        return userSessions.getOrDefault(userId, Set.of());
    }

    public Set<WebSocketSession> getSessionsForConversation(long conversationId) {
        return conversationSubscribers.getOrDefault(conversationId, Set.of());
    }

    /**
     * Returns all connected sessions (for broadcast events like post updates).
     */
    public java.util.Collection<WebSocketSession> getAllSessions() {
        return userSessions.values().stream()
                .flatMap(java.util.Set::stream)
                .toList();
    }

    public Long getUserId(WebSocketSession session) {
        return sessionToUser.get(session.getId());
    }

    public Long getTenantId(WebSocketSession session) {
        return sessionToTenant.get(session.getId());
    }

    public int getTotalConnections() {
        return totalConnections.get();
    }

    public int getTotalUsers() {
        return userSessions.size();
    }
}
