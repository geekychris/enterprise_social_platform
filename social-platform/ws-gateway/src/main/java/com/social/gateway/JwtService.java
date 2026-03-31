package com.social.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Validates JWT tokens from the social app. Also supports the debug header
 * for development (X-Debug-User-Id query param on WebSocket upgrade).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private final SecretKey key;

    public JwtService(@Value("${gateway.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Extract user ID from a JWT token. Returns null if invalid.
     */
    public Long extractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object sub = claims.get("userId");
            if (sub instanceof Number) return ((Number) sub).longValue();
            if (sub instanceof String) return Long.parseLong((String) sub);
            // Try subject field
            String subject = claims.getSubject();
            if (subject != null) return Long.parseLong(subject);
            return null;
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }
}
