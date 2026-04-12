package com.flowdesk.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that read-only operations route to replicas
 * and write operations route to the primary data source.
 *
 * Uses a lightweight in-process setup (no Spring context) to keep the test fast.
 */
class ReadReplicaRoutingIntegrationTest {

    private static final String PRIMARY_KEY   = "primary";
    private static final String REPLICA_0_KEY = "replica-0";
    private static final String REPLICA_1_KEY = "replica-1";
    private static final String REPLICA_2_KEY = "replica-2";

    /** Minimal DataSource stub that records which key was resolved. */
    private static class TrackingDataSource extends org.springframework.jdbc.datasource.AbstractDataSource {
        final String key;
        TrackingDataSource(String key) { this.key = key; }

        @Override public Connection getConnection() { throw new UnsupportedOperationException(); }
        @Override public Connection getConnection(String u, String p) { throw new UnsupportedOperationException(); }
    }

    private ReadReplicaRoutingDataSource routing;

    @BeforeEach
    void setUp() {
        TrackingDataSource primary  = new TrackingDataSource(PRIMARY_KEY);
        TrackingDataSource replica0 = new TrackingDataSource(REPLICA_0_KEY);
        TrackingDataSource replica1 = new TrackingDataSource(REPLICA_1_KEY);
        TrackingDataSource replica2 = new TrackingDataSource(REPLICA_2_KEY);

        Map<Object, Object> targets = new HashMap<>();
        targets.put(PRIMARY_KEY,   primary);
        targets.put(REPLICA_0_KEY, replica0);
        targets.put(REPLICA_1_KEY, replica1);
        targets.put(REPLICA_2_KEY, replica2);

        routing = new ReadReplicaRoutingDataSource();
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(primary);
        routing.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        ReadWriteContext.clear();
    }

    // ── Write path ────────────────────────────────────────────────────────────

    @Test
    void writeOperation_routesToPrimary() {
        ReadWriteContext.setReadOnly(false);
        assertThat(routing.determineCurrentLookupKey()).isEqualTo(PRIMARY_KEY);
    }

    @Test
    void defaultContext_routesToPrimary() {
        // ReadWriteContext defaults to false (write)
        assertThat(routing.determineCurrentLookupKey()).isEqualTo(PRIMARY_KEY);
    }

    // ── Read path ─────────────────────────────────────────────────────────────

    @Test
    void readOnlyOperation_routesToReplica() {
        ReadWriteContext.setReadOnly(true);
        Object key = routing.determineCurrentLookupKey();
        assertThat(key).isIn(REPLICA_0_KEY, REPLICA_1_KEY, REPLICA_2_KEY);
    }

    @Test
    void readOnlyOperations_roundRobinAcrossAllThreeReplicas() {
        ReadWriteContext.setReadOnly(true);

        java.util.Set<Object> seen = new java.util.HashSet<>();
        // 9 calls should hit all 3 replicas at least once
        for (int i = 0; i < 9; i++) {
            seen.add(routing.determineCurrentLookupKey());
        }

        assertThat(seen).containsExactlyInAnyOrder(REPLICA_0_KEY, REPLICA_1_KEY, REPLICA_2_KEY);
    }

    @Test
    void afterReadOnlyCleared_routesBackToPrimary() {
        ReadWriteContext.setReadOnly(true);
        assertThat(routing.determineCurrentLookupKey()).isIn(REPLICA_0_KEY, REPLICA_1_KEY, REPLICA_2_KEY);

        ReadWriteContext.setReadOnly(false);
        assertThat(routing.determineCurrentLookupKey()).isEqualTo(PRIMARY_KEY);
    }

    @Test
    void readOnlyContext_neverRoutesToPrimary() {
        ReadWriteContext.setReadOnly(true);
        for (int i = 0; i < 30; i++) {
            assertThat(routing.determineCurrentLookupKey()).isNotEqualTo(PRIMARY_KEY);
        }
    }
}
