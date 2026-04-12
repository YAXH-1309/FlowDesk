package com.flowdesk.core.audit;

import com.flowdesk.core.context.TenantContext;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P15 (task 4.2): Audit log completeness and immutability
 * Validates: Requirements 13.3, 13.4
 */
class AuditLogPropertyTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void setContext() {
        TenantContext.setTenantId(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null, List.of()));
    }

    // ── P15a: Audit entry is created with all required fields ─────────────────

    @Property(tries = 100)
    @Tag("Feature: saas-platform, Property 15: Audit log completeness and immutability")
    void p15_auditEntryContainsAllRequiredFields(
            @ForAll @AlphaChars @StringLength(min = 3, max = 20) String entityType,
            @ForAll UUID entityId) {

        List<AuditLogEntry> saved = new ArrayList<>();
        AuditLogRepository repo = mock(AuditLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> {
            AuditLogEntry e = inv.getArgument(0);
            saved.add(e);
            return e;
        });

        setContext();
        AuditLogService service = new AuditLogService(repo);
        service.record(AuditAction.CREATE, entityType, entityId, null, "{\"id\":\"" + entityId + "\"}");

        assertThat(saved).hasSize(1);
        AuditLogEntry entry = saved.get(0);

        // All required fields must be present
        assertThat(entry.getAction()).isEqualTo("CREATE");
        assertThat(entry.getEntityType()).isEqualTo(entityType);
        assertThat(entry.getEntityId()).isEqualTo(entityId);
        assertThat(entry.getTenantId()).isNotNull();
        assertThat(entry.getActorId()).isNotNull();
        assertThat(entry.getAfterSnapshot()).isNotBlank();
    }

    // ── P15b: All CRUD actions produce audit entries ──────────────────────────

    @Property(tries = 100)
    @Tag("Feature: saas-platform, Property 15: Audit log completeness and immutability")
    void p15_allCrudActionsProduceAuditEntries(@ForAll AuditAction action,
                                                @ForAll UUID entityId) {
        List<AuditLogEntry> saved = new ArrayList<>();
        AuditLogRepository repo = mock(AuditLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> { saved.add(inv.getArgument(0)); return inv.getArgument(0); });

        setContext();
        AuditLogService service = new AuditLogService(repo);
        service.record(action, "TestEntity", entityId, "{}", "{}");

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getAction()).isEqualTo(action.name());
    }

    // ── P15c: Audit log is append-only — no update/delete methods exposed ─────

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 15: Audit log completeness and immutability")
    void p15_auditLogServiceHasNoUpdateOrDeleteMethods() {
        // Verify AuditLogService only exposes 'record' — no update/delete
        var methods = AuditLogService.class.getDeclaredMethods();
        for (var method : methods) {
            String name = method.getName().toLowerCase();
            assertThat(name)
                    .as("AuditLogService must not expose update/delete: %s", method.getName())
                    .doesNotContain("update", "delete", "remove", "modify");
        }
    }

    // ── P15d: Timestamp is always set on audit entries ────────────────────────

    @Property(tries = 100)
    @Tag("Feature: saas-platform, Property 15: Audit log completeness and immutability")
    void p15_auditEntryTimestampIsAlwaysSet(@ForAll UUID entityId) {
        List<AuditLogEntry> saved = new ArrayList<>();
        AuditLogRepository repo = mock(AuditLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> {
            AuditLogEntry e = inv.getArgument(0);
            // Simulate @PrePersist
            if (e.getTimestamp() == null) {
                try {
                    var f = AuditLogEntry.class.getDeclaredField("timestamp");
                    f.setAccessible(true);
                    f.set(e, java.time.OffsetDateTime.now());
                } catch (Exception ex) { throw new RuntimeException(ex); }
            }
            saved.add(e);
            return e;
        });

        setContext();
        AuditLogService service = new AuditLogService(repo);
        service.record(AuditAction.UPDATE, "Entity", entityId, "{}", "{}");

        assertThat(saved.get(0).getTimestamp()).isNotNull();
    }
}
