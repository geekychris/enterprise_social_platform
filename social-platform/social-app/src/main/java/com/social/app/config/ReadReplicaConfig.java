package com.social.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Routes reads to a replica and writes to the primary.
 * Only active when REPLICA_ENABLED=true and REPLICA_DATASOURCE_URL is set.
 * Uses Spring's @Transactional(readOnly = true) to determine routing.
 */
@Configuration
@ConditionalOnProperty(name = "spring.replica.enabled", havingValue = "true")
public class ReadReplicaConfig {

    @Value("${spring.datasource.url}")
    private String primaryUrl;
    @Value("${spring.datasource.username}")
    private String primaryUsername;
    @Value("${spring.datasource.password:}")
    private String primaryPassword;

    @Value("${spring.replica.url}")
    private String replicaUrl;
    @Value("${spring.replica.username:social}")
    private String replicaUsername;
    @Value("${spring.replica.password:}")
    private String replicaPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        DataSource primary = DataSourceBuilder.create()
                .url(primaryUrl).username(primaryUsername).password(primaryPassword)
                .driverClassName("org.postgresql.Driver").build();

        DataSource replica = DataSourceBuilder.create()
                .url(replicaUrl).username(replicaUsername).password(replicaPassword)
                .driverClassName("org.postgresql.Driver").build();

        var router = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                        ? "replica" : "primary";
            }
        };

        router.setTargetDataSources(Map.of("primary", primary, "replica", replica));
        router.setDefaultTargetDataSource(primary);
        router.afterPropertiesSet();

        return new LazyConnectionDataSourceProxy(router);
    }
}
