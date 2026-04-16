package com.flowdesk.reporting.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Queries per-module key metrics for dashboard display.
 * Uses JdbcTemplate with read-only transactions to route to read replicas.
 */
@Service
public class DashboardMetricsService {

    private final JdbcTemplate jdbc;

    public DashboardMetricsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMetrics(String module, UUID tenantId) {
        return switch (module.toLowerCase()) {
            case "task"       -> taskMetrics(tenantId);
            case "hr"         -> hrMetrics(tenantId);
            case "inventory"  -> inventoryMetrics(tenantId);
            case "accounting" -> accountingMetrics(tenantId);
            case "sales"      -> salesMetrics(tenantId);
            default           -> Map.of();
        };
    }

    // ── task ─────────────────────────────────────────────────────────────────

    private Map<String, Object> taskMetrics(UUID tenantId) {
        Map<String, Object> m = new HashMap<>();
        m.put("activeProjects", queryLong(
                "SELECT COUNT(*) FROM task.projects WHERE tenant_id = ? AND status != 'ARCHIVED'",
                tenantId));
        m.put("openTasks", queryLong(
                "SELECT COUNT(*) FROM task.tasks WHERE tenant_id = ? AND status != 'DONE'",
                tenantId));
        m.put("tasksUpdatedLast24h", queryLong(
                "SELECT COUNT(*) FROM task.tasks WHERE tenant_id = ? AND updated_at >= NOW() - INTERVAL '24 hours'",
                tenantId));
        return m;
    }

    // ── hr ───────────────────────────────────────────────────────────────────

    private Map<String, Object> hrMetrics(UUID tenantId) {
        Map<String, Object> m = new HashMap<>();
        m.put("activeEmployees", queryLong(
                "SELECT COUNT(*) FROM hr.employees WHERE tenant_id = ? AND status = 'ACTIVE'",
                tenantId));
        m.put("attendanceToday", queryLong(
                "SELECT COUNT(*) FROM hr.attendance WHERE tenant_id = ? AND date = ?",
                tenantId, LocalDate.now()));
        m.put("pendingPerformanceReviews", queryLong(
                "SELECT COUNT(*) FROM hr.performance_reviews WHERE tenant_id = ? AND status = 'PENDING'",
                tenantId));
        return m;
    }

    // ── inventory ─────────────────────────────────────────────────────────────

    private Map<String, Object> inventoryMetrics(UUID tenantId) {
        Map<String, Object> m = new HashMap<>();
        m.put("lowStockSkus", queryLong(
                "SELECT COUNT(*) FROM inventory.products p " +
                "JOIN inventory.inventory_levels il ON il.product_id = p.id " +
                "WHERE p.tenant_id = ? AND il.quantity_on_hand <= p.reorder_threshold",
                tenantId));
        m.put("openPurchaseOrders", queryLong(
                "SELECT COUNT(*) FROM inventory.purchase_orders WHERE tenant_id = ? AND status = 'OPEN'",
                tenantId));
        return m;
    }

    // ── accounting ────────────────────────────────────────────────────────────

    private Map<String, Object> accountingMetrics(UUID tenantId) {
        Map<String, Object> m = new HashMap<>();
        m.put("overdueArInvoices", queryLong(
                "SELECT COUNT(*) FROM accounting.ar_invoices WHERE tenant_id = ? AND status = 'OVERDUE'",
                tenantId));
        m.put("outstandingApTotal", queryDecimal(
                "SELECT COALESCE(SUM(amount_due), 0) FROM accounting.ap_invoices WHERE tenant_id = ? AND status != 'PAID'",
                tenantId));
        return m;
    }

    // ── sales ─────────────────────────────────────────────────────────────────

    private Map<String, Object> salesMetrics(UUID tenantId) {
        Map<String, Object> m = new HashMap<>();
        m.put("openOpportunities", queryLong(
                "SELECT COUNT(*) FROM sales.opportunities WHERE tenant_id = ? AND status = 'OPEN'",
                tenantId));
        m.put("ordersOnCreditHold", queryLong(
                "SELECT COUNT(*) FROM sales.orders WHERE tenant_id = ? AND status = 'CREDIT_HOLD'",
                tenantId));
        m.put("customers", queryLong(
                "SELECT COUNT(*) FROM sales.customers WHERE tenant_id = ?",
                tenantId));
        return m;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private long queryLong(String sql, Object... args) {
        Long result = jdbc.queryForObject(sql, Long.class, args);
        return result != null ? result : 0L;
    }

    private java.math.BigDecimal queryDecimal(String sql, Object... args) {
        java.math.BigDecimal result = jdbc.queryForObject(sql, java.math.BigDecimal.class, args);
        return result != null ? result : java.math.BigDecimal.ZERO;
    }
}
