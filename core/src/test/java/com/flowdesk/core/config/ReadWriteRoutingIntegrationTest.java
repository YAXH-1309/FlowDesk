package com.flowdesk.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that read-only transactions are routed to a replica
 * data source and write transactions are routed to the primary.
 *
 * <p>Uses a single PostgreSQL Testcontainer mapped to all four data source URLs
 * (primary + 3 replicas) so the test is self-contained. The routing key returned
 * by {@link ReadReplicaRoutingDataSource} is captured via a spy subclass.</p>
 *
 * <p>Requirements: 20.3</p>
 */
@Testcontainers
@SpringBootTest(classes = ReadWriteRoutingIntegrationTest.TestConfig.class)
@EnableAutoConfiguration(exclude = {FlywayAutoConfiguration.class, KafkaAutoConfiguration.class})
class ReadWriteRoutingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    /** Captures the routing keys chosen during each test. */
    static final List<Object> capturedKeys = new ArrayList<>();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        String url  = POSTGRES.getJdbcUrl();
        String user = POSTGRES.getUsername();
        String pass = POSTGRES.getPassword();

        // All four pools point at the same container — routing key is what matters
        for (String prefix : List.of("primary", "replica0", "replica1", "replica2")) {
            registry.add("spring.datasource." + prefix + ".jdbc-url",      () -> url);
            registry.add("spring.datasource." + prefix + ".username",      () -> user);
            registry.add("spring.datasource." + prefix + ".password",      () -> pass);
            registry.add("spring.datasource." + prefix + ".driver-class-name",
                    () -> "org.postgresql.Driver");
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearKeys() {
        capturedKeys.clear();
        ReadWriteContext.clear();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Write transaction routes to primary data source")
    @Transactional          // readOnly defaults to false → primary
    void writeShouldUsePrimary() {
        jdbcTemplate.execute("SELECT 1");   // triggers connection acquisition

        assertThat(capturedKeys).isNotEmpty();
        assertThat(capturedKeys.get(0)).isEqualTo("primary");
    }

    @Test
    @DisplayName("Read-only transaction routes to a replica data source")
    @Transactional(readOnly = true)         // aspect sets ReadWriteContext → replica
    void readOnlyShouldUseReplica() {
        jdbcTemplate.execute("SELECT 1");

        assertThat(capturedKeys).isNotEmpty();
        assertThat(capturedKeys.get(0)).asString().startsWith("replica-");
    }

    @Test
    @DisplayName("Round-robin distributes read-only requests across all three replicas")
    void roundRobinCoversAllReplicas() {
        // Manually drive the context to simulate three consecutive read-only calls
        for (int i = 0; i < 3; i++) {
            ReadWriteContext.setReadOnly(true);
            try {
                jdbcTemplate.execute("SELECT 1");
            } finally {
                ReadWriteContext.clear();
            }
        }

        assertThat(capturedKeys)
                .hasSize(3)
                .containsExactlyInAnyOrder("replica-0", "replica-1", "replica-2");
    }

    @Test
    @DisplayName("ReadWriteContext defaults to write (primary) when not set")
    void defaultContextShouldUsePrimary() {
        // No @Transactional annotation — context is false by default
        jdbcTemplate.execute("SELECT 1");

        assertThat(capturedKeys).isNotEmpty();
        assertThat(capturedKeys.get(0)).isEqualTo("primary");
    }

    // ── Test configuration ────────────────────────────────────────────────────

    @TestConfiguration
    @Import({DataSourceConfig.class, ReadOnlyTransactionAspect.class})
    static class TestConfig {

        /**
         * Overrides the routing data source with a spy that records the chosen key
         * into {@link #capturedKeys} before delegating to the real implementation.
         */
        @Bean
        public DataSource routingDataSource(
                javax.sql.DataSource primaryDataSource,
                javax.sql.DataSource replica0DataSource,
                javax.sql.DataSource replica1DataSource,
                javax.sql.DataSource replica2DataSource) {

            java.util.Map<Object, Object> targets = new java.util.HashMap<>();
            targets.put("primary",   primaryDataSource);
            targets.put("replica-0", replica0DataSource);
            targets.put("replica-1", replica1DataSource);
            targets.put("replica-2", replica2DataSource);

            ReadReplicaRoutingDataSource routing = new ReadReplicaRoutingDataSource() {
                @Override
                protected Object determineCurrentLookupKey() {
                    Object key = super.determineCurrentLookupKey();
                    capturedKeys.add(key);
                    return key;
                }
            };
            routing.setTargetDataSources(targets);
            routing.setDefaultTargetDataSource(primaryDataSource);
            routing.afterPropertiesSet();
            return routing;
        }
    }
}
