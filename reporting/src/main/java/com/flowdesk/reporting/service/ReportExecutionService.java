package com.flowdesk.reporting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.exception.BusinessRuleException;
import com.flowdesk.core.exception.ResourceNotFoundException;
import com.flowdesk.reporting.domain.ReportDefinition;
import com.flowdesk.reporting.dto.ReportResult;
import com.flowdesk.reporting.repository.ReportDefinitionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Executes report definitions against the database using JdbcTemplate.
 *
 * RBAC note: Cross-module role enforcement (e.g., only FINANCE role can query accounting data)
 * is handled by @PreAuthorize on the controller endpoint. Within this service, tenant isolation
 * is enforced by always filtering WHERE tenant_id = ? so users only see their own tenant's data.
 */
@Service
public class ReportExecutionService {

    private static final int SYNC_ROW_LIMIT = 10_000;
    static final int PAGE_SIZE = 1_000;

    private static final Map<String, Set<String>> ALLOWED_COLUMNS = Map.of(
        "task",       Set.of("id", "title", "status", "assignee_id", "created_at", "updated_at"),
        "hr",         Set.of("id", "full_name", "department", "job_title", "employment_status", "start_date"),
        "inventory",  Set.of("id", "product_name", "quantity_on_hand", "reorder_threshold", "unit_cost"),
        "accounting", Set.of("id", "code", "name", "type", "balance"),
        "sales",      Set.of("id", "company_name", "contact_email", "credit_limit", "payment_terms")
    );

    private static final Map<String, String> MODULE_TABLE = Map.of(
        "task",       "task_schema.tasks",
        "hr",         "hr_schema.employees",
        "inventory",  "inventory_schema.skus",
        "accounting", "accounting_schema.accounts",
        "sales",      "sales_schema.customers"
    );

    private final ReportDefinitionRepository reportRepo;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReportExecutionService(ReportDefinitionRepository reportRepo,
                                   JdbcTemplate jdbcTemplate,
                                   ObjectMapper objectMapper) {
        this.reportRepo = reportRepo;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ReportResult execute(UUID reportId, UUID tenantId, String cursor, int pageSize) {
        pageSize = Math.min(pageSize, PAGE_SIZE);
        ReportDefinition def = reportRepo.findByIdAndTenantId(reportId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        String module = def.getSourceModule();
        String table = MODULE_TABLE.get(module);
        if (table == null) {
            throw new BusinessRuleException("Unknown source module: " + module);
        }

        // Validate and build SELECT columns
        String selectClause = buildSelectClause(module, def.getOutputColumns());

        // Build WHERE clause from filterCriteria JSON + mandatory tenant_id filter
        List<Object> params = new ArrayList<>();
        String whereClause = buildWhereClause(def.getFilterCriteria(), tenantId, params);

        // Build GROUP BY clause
        String groupByClause = buildGroupByClause(module, def.getGroupingFields());

        // Count total rows first
        String countSql = "SELECT COUNT(*) FROM " + table + whereClause + groupByClause;
        Integer totalRows = jdbcTemplate.queryForObject(countSql, Integer.class, params.toArray());
        if (totalRows == null) totalRows = 0;

        if (totalRows > SYNC_ROW_LIMIT) {
            throw new BusinessRuleException("Result set too large — use async export");
        }

        // Decode cursor (base64-encoded offset)
        int offset = decodeCursor(cursor);

        // Fetch page
        String dataSql = "SELECT " + selectClause + " FROM " + table + whereClause + groupByClause
                + " LIMIT ? OFFSET ?";
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add(offset);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(dataSql, pageParams.toArray());

        int nextOffset = offset + rows.size();
        String nextCursor = nextOffset < totalRows ? encodeCursor(nextOffset) : null;

        return new ReportResult(rows, totalRows, rows.size(), nextCursor);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildSelectClause(String module, String[] outputColumns) {
        Set<String> allowed = ALLOWED_COLUMNS.get(module);
        if (outputColumns == null || outputColumns.length == 0) {
            // Default: all allowed columns
            return String.join(", ", allowed);
        }
        List<String> validated = new ArrayList<>();
        for (String col : outputColumns) {
            if (!allowed.contains(col)) {
                throw new BusinessRuleException("Column not allowed for module '" + module + "': " + col);
            }
            validated.add(col);
        }
        return String.join(", ", validated);
    }

    private String buildWhereClause(String filterCriteria, UUID tenantId, List<Object> params) {
        StringBuilder sb = new StringBuilder(" WHERE tenant_id = ?");
        params.add(tenantId);

        if (filterCriteria != null && !filterCriteria.isBlank()) {
            try {
                Map<String, Object> filters = objectMapper.readValue(
                        filterCriteria, new TypeReference<Map<String, Object>>() {});
                for (Map.Entry<String, Object> entry : filters.entrySet()) {
                    // Only allow simple key=value filters (no injection via key)
                    String col = entry.getKey().replaceAll("[^a-zA-Z0-9_]", "");
                    sb.append(" AND ").append(col).append(" = ?");
                    params.add(entry.getValue());
                }
            } catch (Exception e) {
                throw new BusinessRuleException("Invalid filterCriteria JSON: " + e.getMessage());
            }
        }

        return sb.toString();
    }

    private String buildGroupByClause(String module, String[] groupingFields) {
        if (groupingFields == null || groupingFields.length == 0) {
            return "";
        }
        Set<String> allowed = ALLOWED_COLUMNS.get(module);
        List<String> validated = new ArrayList<>();
        for (String col : groupingFields) {
            if (!allowed.contains(col)) {
                throw new BusinessRuleException("Grouping column not allowed for module '" + module + "': " + col);
            }
            validated.add(col);
        }
        return " GROUP BY " + String.join(", ", validated);
    }

    private int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            return Integer.parseInt(new String(Base64.getDecoder().decode(cursor)));
        } catch (Exception e) {
            return 0;
        }
    }

    private String encodeCursor(int offset) {
        return Base64.getEncoder().encodeToString(String.valueOf(offset).getBytes());
    }
}
