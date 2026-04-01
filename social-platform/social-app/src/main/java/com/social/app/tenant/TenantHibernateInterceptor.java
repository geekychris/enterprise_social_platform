package com.social.app.tenant;

import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Enables the Hibernate tenant filter on every repository call.
 * This automatically appends "AND tenant_id = :tenantId" to all queries
 * on entities annotated with @Filter(name = "tenantFilter").
 */
@Aspect
@Component
public class TenantHibernateInterceptor {

    private final EntityManager entityManager;

    public TenantHibernateInterceptor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Before("execution(* com.social.app.persistence.repository..*(..))")
    public void enableTenantFilter() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        }
    }
}
