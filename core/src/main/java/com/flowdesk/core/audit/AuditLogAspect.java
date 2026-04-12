package com.flowdesk.core.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AOP aspect that intercepts methods annotated with {@link AuditLog}
 * and writes an audit entry before and after execution.
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AuditLogAspect(AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        Object result = pjp.proceed();

        try {
            UUID entityId = extractEntityId(result);
            String afterSnapshot = toJson(result);
            auditLogService.record(auditLog.action(), auditLog.entityType(), entityId, null, afterSnapshot);
        } catch (Exception e) {
            log.warn("Audit log write failed for {}.{}: {}",
                    pjp.getSignature().getDeclaringTypeName(),
                    pjp.getSignature().getName(), e.getMessage());
        }

        return result;
    }

    private UUID extractEntityId(Object result) {
        if (result == null) return UUID.fromString("00000000-0000-0000-0000-000000000000");
        try {
            var method = result.getClass().getMethod("getId");
            Object id = method.invoke(result);
            if (id instanceof UUID uuid) return uuid;
            if (id instanceof String s) return UUID.fromString(s);
        } catch (Exception ignored) {}
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
