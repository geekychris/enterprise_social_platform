package com.social.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Development-only filter that allows bypassing authentication by setting
 * the X-Debug-User-Id header. Controlled by social.auth.debug-bypass property.
 */
@Component
public class DebugAuthFilter extends OncePerRequestFilter {

    private final boolean debugBypass;

    public DebugAuthFilter(@Value("${social.auth.debug-bypass:false}") boolean debugBypass) {
        this.debugBypass = debugBypass;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (debugBypass && SecurityContextHolder.getContext().getAuthentication() == null) {
            String debugUserId = request.getHeader("X-Debug-User-Id");
            if (debugUserId != null && !debugUserId.isBlank()) {
                try {
                    long userId = Long.parseLong(debugUserId);
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId, null, List.of()
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (NumberFormatException ignored) {
                    // Invalid header value, skip
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
