package com.social.app.tenant;

/**
 * Thread-local holder for the current tenant ID.
 * Set early in the request filter chain, used by Hibernate filters and cache keys.
 */
public final class TenantContext {
    private static final ThreadLocal<Long> currentTenant = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        currentTenant.set(tenantId);
    }

    public static Long getTenantId() {
        return currentTenant.get();
    }

    public static long requireTenantId() {
        Long id = currentTenant.get();
        if (id == null) throw new IllegalStateException("No tenant set in current context");
        return id;
    }

    public static void clear() {
        currentTenant.remove();
    }
}
