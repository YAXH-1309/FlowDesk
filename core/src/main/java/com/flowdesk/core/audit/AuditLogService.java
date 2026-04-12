package com.flowdesk.core.audit;

import com.flowdesk.core.context.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes append-only entries to {@code core_schema.audit_log}.
 * Uses REQUIRES_NEW so the audit write commits independently of the caller's transaction.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, String entityType, UUID entityId,
                       String beforeSnapshot, String afterSnapshot) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setTenantId(currentTenantId());
        entry.setActorId(currentActorId());
        entry.setAction(action.name());
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBeforeSnapshot(beforeSnapshot);
        entry.setAfterSnapshot(afterSnapshot);
        repository.save(entry);
    }

    private UUID currentTenantId() {
        UUID id = TenantContext.getTenantId();
        return id != null ? id : UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    private UUID currentActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String principal) {
            try { return UUID.fromString(principal); } catch (IllegalArgumentException ignored) {}
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}
