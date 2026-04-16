package com.flowdesk.reporting.readmodel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CQRS Read Model Service.
 *
 * All GET /api/v1/reporting/... queries go through this service.
 * Data is sourced exclusively from Elasticsearch — PostgreSQL is never queried for reads.
 * The read model is eventually consistent: Kafka consumers update it asynchronously.
 */
@Service
public class ReadModelService {

    private static final Logger log = LoggerFactory.getLogger(ReadModelService.class);

    private final ReadModelRepository repository;
    private final ElasticsearchOperations elasticsearchOperations;

    public ReadModelService(ReadModelRepository repository,
                            ElasticsearchOperations elasticsearchOperations) {
        this.repository = repository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    // ── Write (called by Kafka consumers) ─────────────────────────────────────

    public void upsert(ReadModelDocument doc) {
        doc.setIndexedAt(Instant.now());
        repository.save(doc);
        log.debug("Indexed read model document: {}", doc.getId());
    }

    // ── Dashboard metrics (read model only) ───────────────────────────────────

    /**
     * Returns aggregated metrics for a module dashboard from Elasticsearch.
     * Replaces the PostgreSQL-based DashboardMetricsService for GET endpoints.
     */
    public Map<String, Object> getDashboardMetrics(String module, String tenantId) {
        return switch (module.toLowerCase()) {
            case "hr"         -> hrDashboard(tenantId);
            case "inventory"  -> inventoryDashboard(tenantId);
            case "accounting" -> accountingDashboard(tenantId);
            case "sales"      -> salesDashboard(tenantId);
            case "task"       -> taskDashboard(tenantId);
            default           -> Map.of();
        };
    }

    private Map<String, Object> hrDashboard(String tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activeEmployees", countByModuleAndStatus(tenantId, "hr", "ACTIVE"));
        m.put("totalEmployees", countByModule(tenantId, "hr"));
        return m;
    }

    private Map<String, Object> inventoryDashboard(String tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lowStockAlerts", countByModuleAndStatus(tenantId, "inventory", "LOW_STOCK"));
        m.put("totalSkus", countByModule(tenantId, "inventory"));
        return m;
    }

    private Map<String, Object> accountingDashboard(String tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("overdueInvoices", countByModuleAndStatus(tenantId, "accounting", "OVERDUE"));
        m.put("totalInvoices", countByModule(tenantId, "accounting"));
        return m;
    }

    private Map<String, Object> salesDashboard(String tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("confirmedOrders", countByModuleAndStatus(tenantId, "sales", "CONFIRMED"));
        m.put("creditHoldOrders", countByModuleAndStatus(tenantId, "sales", "CREDIT_HOLD"));
        m.put("totalOrders", countByModule(tenantId, "sales"));
        return m;
    }

    private Map<String, Object> taskDashboard(String tenantId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("openTasks", countByModuleAndStatus(tenantId, "task", "IN_PROGRESS"));
        m.put("totalTasks", countByModule(tenantId, "task"));
        return m;
    }

    // ── Report execution (read model only) ────────────────────────────────────

    /**
     * Executes a report query against Elasticsearch.
     * Replaces the PostgreSQL-based ReportExecutionService for GET endpoints.
     */
    public ReadModelPage queryModule(String module, String tenantId,
                                     Map<String, String> filters,
                                     int page, int pageSize) {
        Criteria criteria = new Criteria("tenantId").is(tenantId)
                .and(new Criteria("module").is(module));

        if (filters != null) {
            for (Map.Entry<String, String> entry : filters.entrySet()) {
                // Only allow filtering on known safe fields
                String field = sanitizeField(entry.getKey());
                if (field != null) {
                    criteria = criteria.and(new Criteria(field).is(entry.getValue()));
                }
            }
        }

        CriteriaQuery query = new CriteriaQuery(criteria)
                .setPageable(PageRequest.of(page, pageSize));

        SearchHits<ReadModelDocument> hits = elasticsearchOperations.search(query, ReadModelDocument.class);
        List<Map<String, Object>> rows = hits.stream()
                .map(SearchHit::getContent)
                .map(this::documentToRow)
                .collect(Collectors.toList());

        long total = hits.getTotalHits();
        return new ReadModelPage(rows, total, page, pageSize);
    }

    // ── Full-text search (read model only) ────────────────────────────────────

    public List<Map<String, Object>> search(String query, String tenantId) {
        Criteria criteria = new Criteria("tenantId").is(tenantId)
                .and(new Criteria("displayName").contains(query)
                        .or(new Criteria("description").contains(query)));

        SearchHits<ReadModelDocument> hits = elasticsearchOperations.search(
                new CriteriaQuery(criteria), ReadModelDocument.class);

        return hits.stream()
                .map(SearchHit::getContent)
                .map(doc -> Map.<String, Object>of(
                        "id", doc.getEntityId() != null ? doc.getEntityId() : doc.getId(),
                        "module", doc.getModule() != null ? doc.getModule() : "",
                        "entityType", doc.getEntityType() != null ? doc.getEntityType() : "",
                        "displayName", doc.getDisplayName() != null ? doc.getDisplayName() : "",
                        "status", doc.getStatus() != null ? doc.getStatus() : ""
                ))
                .collect(Collectors.toList());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long countByModule(String tenantId, String module) {
        return repository.countByTenantIdAndModule(tenantId, module);
    }

    private long countByModuleAndStatus(String tenantId, String module, String status) {
        return repository.countByTenantIdAndModuleAndStatus(tenantId, module, status);
    }

    private Map<String, Object> documentToRow(ReadModelDocument doc) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", doc.getEntityId());
        row.put("module", doc.getModule());
        row.put("entityType", doc.getEntityType());
        row.put("displayName", doc.getDisplayName());
        row.put("status", doc.getStatus());
        row.put("eventTime", doc.getEventTime());
        if (doc.getAttributes() != null) {
            row.putAll(doc.getAttributes());
        }
        return row;
    }

    private String sanitizeField(String field) {
        // Whitelist of filterable fields to prevent injection
        return switch (field) {
            case "status", "entityType", "module" -> field;
            default -> null;
        };
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    public record ReadModelPage(List<Map<String, Object>> rows, long total, int page, int pageSize) {}
}
