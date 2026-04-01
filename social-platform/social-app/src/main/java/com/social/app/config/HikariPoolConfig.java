package com.social.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * When read replicas are NOT enabled, this logs the single-DB configuration.
 * No special config needed — Spring Boot's default HikariCP is used.
 *
 * To enable read replicas:
 *   REPLICA_ENABLED=true
 *   REPLICA_DATASOURCE_URL=jdbc:postgresql://replica-host:5432/social_enterprise
 */
@Configuration
@ConditionalOnProperty(name = "spring.replica.enabled", havingValue = "false", matchIfMissing = true)
public class HikariPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(HikariPoolConfig.class);

    public HikariPoolConfig() {
        log.info("Read replicas disabled — using single primary datasource. Set REPLICA_ENABLED=true to enable.");
    }
}
