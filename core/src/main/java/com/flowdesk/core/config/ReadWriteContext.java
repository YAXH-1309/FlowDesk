package com.flowdesk.core.config;

/**
 * Thread-local context that tracks whether the current operation is read-only.
 * Used by {@link ReadReplicaRoutingDataSource} to route queries to read replicas.
 */
public final class ReadWriteContext {

    private static final ThreadLocal<Boolean> READ_ONLY = ThreadLocal.withInitial(() -> false);

    private ReadWriteContext() {}

    public static void setReadOnly(boolean readOnly) {
        READ_ONLY.set(readOnly);
    }

    public static boolean isReadOnly() {
        return Boolean.TRUE.equals(READ_ONLY.get());
    }

    public static void clear() {
        READ_ONLY.remove();
    }
}
