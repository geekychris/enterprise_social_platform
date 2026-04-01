package com.social.app.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts tenant from the request and sets it in TenantContext.
 * Resolution order:
 * 1. X-Tenant-Id header (for dev/testing)
 * 2. Subdomain (acme.worksphere.com -> tenant slug "acme")
 * 3. Default to tenant 1 (backward compatibility)
 */
@Component
@Order(1)  // Run before auth filters
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            Long tenantId = resolveTenant(request);
            TenantContext.setTenantId(tenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Long resolveTenant(HttpServletRequest request) {
        // 1. Explicit header (dev/testing)
        String headerTenant = request.getHeader("X-Tenant-Id");
        if (headerTenant != null) {
            try { return Long.parseLong(headerTenant); } catch (NumberFormatException ignored) {}
        }

        // 2. Subdomain: acme.worksphere.com -> look up "acme"
        // For now, default to tenant 1. Subdomain resolution can be added when DNS is configured.

        // 3. Default
        return 1L;
    }
}
