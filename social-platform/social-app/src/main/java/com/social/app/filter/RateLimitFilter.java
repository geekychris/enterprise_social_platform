package com.social.app.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Cache<String, AtomicInteger> rateLimitCache;

    public RateLimitFilter() {
        this.rateLimitCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(100_000)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Skip non-API requests
        if (!request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String clientKey = getClientKey(request);
        int limit = getLimit(request.getRequestURI());

        AtomicInteger counter = rateLimitCache.get(clientKey, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();

        if (current > limit) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retryAfterSeconds\":60}");
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - current)));

        chain.doFilter(request, response);
    }

    private String getClientKey(HttpServletRequest request) {
        // Use userId if authenticated, IP otherwise
        String debugId = request.getHeader("X-Debug-User-Id");
        if (debugId != null) {
            return "user:" + debugId;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null) {
            return "token:" + auth.hashCode();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private int getLimit(String uri) {
        if (uri.contains("/messages") || uri.contains("/conversations")) return 300;
        if (uri.contains("/feed")) return 120;
        if (uri.contains("/search")) return 60;
        if (uri.contains("/ai/")) return 60;
        return 200; // default per minute
    }
}
