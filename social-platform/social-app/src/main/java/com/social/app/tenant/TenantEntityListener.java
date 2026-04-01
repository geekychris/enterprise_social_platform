package com.social.app.tenant;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * JPA entity listener that auto-sets tenant_id from TenantContext before persist and update.
 * Configured globally via META-INF/orm.xml.
 */
public class TenantEntityListener {

    @PrePersist
    @PreUpdate
    public void setTenantId(Object entity) {
        try {
            var getter = entity.getClass().getMethod("getTenantId");
            var setter = entity.getClass().getMethod("setTenantId", Long.class);
            Object current = getter.invoke(entity);
            if (current == null) {
                Long tenantId = TenantContext.getTenantId();
                if (tenantId == null) tenantId = 1L;
                setter.invoke(entity, tenantId);
            }
        } catch (NoSuchMethodException e) {
            // Entity doesn't have tenantId (e.g., TenantEntity) — skip
        } catch (Exception e) {
            // Reflection error — don't fail the operation
        }
    }
}
