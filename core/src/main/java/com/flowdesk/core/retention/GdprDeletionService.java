package com.flowdesk.core.retention;

import com.flowdesk.core.audit.AuditAction;
import com.flowdesk.core.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GDPR deletion requests by anonymizing PII across all module schemas.
 */
@Service
public class GdprDeletionService {

    private static final Logger log = LoggerFactory.getLogger(GdprDeletionService.class);
    private static final String REDACTED = "[REDACTED]";

    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;

    public GdprDeletionService(JdbcTemplate jdbc, AuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void processDeleteRequest(String subjectEmail) {
        log.info("Processing GDPR deletion request for subject: {}", subjectEmail);

        // Anonymize in core_schema.users
        int usersUpdated = jdbc.update(
                "UPDATE core_schema.users SET email = ?, password_hash = ? WHERE email = ?",
                REDACTED + "@redacted.invalid", REDACTED, subjectEmail);

        // Anonymize in hr_schema.employees (full_name)
        int employeesUpdated = jdbc.update(
                "UPDATE hr_schema.employees SET full_name = ? " +
                "WHERE tenant_id IN (SELECT tenant_id FROM core_schema.users WHERE email = ?)",
                REDACTED, REDACTED + "@redacted.invalid");

        // Anonymize in sales_schema.customers (contact_email)
        int customersUpdated = jdbc.update(
                "UPDATE sales_schema.customers SET contact_email = ? WHERE contact_email = ?",
                REDACTED, subjectEmail);

        log.info("GDPR anonymization complete: users={}, employees={}, customers={}",
                usersUpdated, employeesUpdated, customersUpdated);

        // Publish audit event
        auditLogService.record(AuditAction.DELETE, "User", null,
                "{\"email\":\"" + subjectEmail + "\"}",
                "{\"status\":\"anonymized\"}");
    }
}
