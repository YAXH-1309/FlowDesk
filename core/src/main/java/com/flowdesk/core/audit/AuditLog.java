package com.flowdesk.core.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for automatic audit logging.
 * The {@link AuditLogAspect} intercepts annotated methods and writes
 * an entry to {@code core_schema.audit_log}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    AuditAction action();
    String entityType();
}
