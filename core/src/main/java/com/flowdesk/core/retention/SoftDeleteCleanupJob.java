package com.flowdesk.core.retention;

import com.flowdesk.core.audit.AuditAction;
import com.flowdesk.core.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Weekly job that hard-deletes rows where deleted_at < NOW() - 90 days.
 * Logs the count of deleted rows per table to the audit log.
 */
@Component
public class SoftDeleteCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(SoftDeleteCleanupJob.class);

    private static final List<String> SOFT_DELETE_TABLES = List.of(
            "task_schema.projects",
            "task_schema.tasks"
    );

    private final JdbcTemplate jdbc;
    private final AuditLogService auditLogService;

    public SoftDeleteCleanupJob(JdbcTemplate jdbc, AuditLogService auditLogService) {
        this.jdbc = jdbc;
        this.auditLogService = auditLogService;
    }

    @Scheduled(cron = "0 0 3 * * SUN")
    public void cleanupSoftDeleted() {
        for (String table : SOFT_DELETE_TABLES) {
            try {
                int deleted = jdbc.update(
                        "DELETE FROM " + table + " WHERE deleted_at < NOW() - INTERVAL '90 days'");
                if (deleted > 0) {
                    log.info("Hard-deleted {} rows from {}", deleted, table);
                    auditLogService.record(AuditAction.DELETE, table, null,
                            null, "{\"deletedCount\":" + deleted + "}");
                }
            } catch (Exception e) {
                log.error("Soft-delete cleanup failed for {}: {}", table, e.getMessage());
            }
        }
    }
}
