package com.social.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;
    private final boolean debugBypass;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil,
                                     @Value("${social.auth.debug-bypass:false}") boolean debugBypass) {
        this.jwtUtil = jwtUtil;
        this.debugBypass = debugBypass;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // Try JWT token from query param
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");

        if (token != null && !token.isBlank()) {
            Long userId = jwtUtil.getUserIdFromToken(token);
            if (userId != null) {
                attributes.put("userId", userId);
                attributes.put("principal", new WebSocketPrincipal(userId));
                return true;
            }
        }

        // Try X-Debug-User-Id header (debug mode only)
        if (debugBypass && request instanceof ServletServerHttpRequest servletRequest) {
            String debugUserId = servletRequest.getServletRequest().getHeader("X-Debug-User-Id");
            if (debugUserId != null && !debugUserId.isBlank()) {
                try {
                    long userId = Long.parseLong(debugUserId);
                    attributes.put("userId", userId);
                    attributes.put("principal", new WebSocketPrincipal(userId));
                    return true;
                } catch (NumberFormatException ignored) {
                    // Invalid header value
                }
            }
        }

        // Reject unauthenticated connections
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No-op
    }

    /**
     * Simple Principal implementation for WebSocket sessions.
     */
    public static class WebSocketPrincipal implements Principal {
        private final long userId;

        public WebSocketPrincipal(long userId) {
            this.userId = userId;
        }

        @Override
        public String getName() {
            return String.valueOf(userId);
        }

        public long getUserId() {
            return userId;
        }
    }
}
