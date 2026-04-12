package com.flowdesk.core.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configures HikariCP connection pools for the primary database and three read replicas,
 * wires them into a {@link ReadReplicaRoutingDataSource}, and exposes the result
 * wrapped in a {@link LazyConnectionDataSourceProxy} as the primary {@link DataSource} bean.
 */
@Configuration
@EnableAspectJAutoProxy
public class DataSourceConfig {

    // ── Primary ──────────────────────────────────────────────────────────────

    @Bean
    @ConfigurationProperties("spring.datasource.primary")
    public HikariDataSource primaryDataSource() {
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        ds.setMaximumPoolSize(50);
        ds.setMinimumIdle(10);
        ds.setConnectionTimeout(30_000);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1_800_000);
        ds.setPoolName("primary-pool");
        return ds;
    }

    // ── Replicas ─────────────────────────────────────────────────────────────

    @Bean
    @ConfigurationProperties("spring.datasource.replica0")
    public HikariDataSource replica0DataSource() {
        return buildReplicaDataSource("replica0-pool");
    }

    @Bean
    @ConfigurationProperties("spring.datasource.replica1")
    public HikariDataSource replica1DataSource() {
        return buildReplicaDataSource("replica1-pool");
    }

    @Bean
    @ConfigurationProperties("spring.datasource.replica2")
    public HikariDataSource replica2DataSource() {
        return buildReplicaDataSource("replica2-pool");
    }

    private HikariDataSource buildReplicaDataSource(String poolName) {
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        ds.setMaximumPoolSize(30);
        ds.setMinimumIdle(10);
        ds.setConnectionTimeout(30_000);
        ds.setIdleTimeout(600_000);
        ds.setMaxLifetime(1_800_000);
        ds.setPoolName(poolName);
        return ds;
    }

    // ── Routing DataSource ────────────────────────────────────────────────────

    @Bean
    public DataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("replica0DataSource") DataSource replica0,
            @Qualifier("replica1DataSource") DataSource replica1,
            @Qualifier("replica2DataSource") DataSource replica2) {

        Map<Object, Object> targets = new HashMap<>();
        targets.put("primary",   primary);
        targets.put("replica-0", replica0);
        targets.put("replica-1", replica1);
        targets.put("replica-2", replica2);

        ReadReplicaRoutingDataSource routing = new ReadReplicaRoutingDataSource();
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(primary);
        routing.afterPropertiesSet();
        return routing;
    }

    // ── Lazy proxy (defers connection acquisition until actually needed) ───────

    @Primary
    @Bean
    public DataSource dataSource(@Qualifier("routingDataSource") DataSource routingDataSource) {
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }
}
