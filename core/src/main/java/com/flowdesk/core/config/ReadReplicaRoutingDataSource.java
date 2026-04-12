package com.flowdesk.core.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Routes database connections to the primary or one of the read replicas
 * using round-robin load balancing for read-only operations.
 */
public class ReadReplicaRoutingDataSource extends AbstractRoutingDataSource {

    private static final List<String> REPLICA_KEYS = List.of("replica-0", "replica-1", "replica-2");

    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    protected Object determineCurrentLookupKey() {
        if (ReadWriteContext.isReadOnly()) {
            int index = Math.abs(counter.getAndIncrement() % REPLICA_KEYS.size());
            return REPLICA_KEYS.get(index);
        }
        return "primary";
    }
}
