package com.flowdesk.reporting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.context.TenantContext;
import com.flowdesk.core.exception.BusinessRuleException;
import com.flowdesk.core.exception.ResourceNotFoundException;
import com.flowdesk.reporting.domain.ReportDefinition;
import com.flowdesk.reporting.domain.ReportExport;
import com.flowdesk.reporting.dto.DefineReportRequest;
import com.flowdesk.reporting.dto.ReportResult;
import com.flowdesk.reporting.readmodel.ReadModelService;
import com.flowdesk.reporting.repository.ReportDefinitionRepository;
import com.flowdesk.reporting.repository.ReportExportRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ReportingService {

    private static final long DASHBOARD_TTL_SECONDS = 300; // 5 min
    private static final int SYNC_ROW_LIMIT = 10_000;

    private final ReportDefinitionRepository reportRepo;
    private final ReportExportRepository exportRepo;
    private final StringRedisTemplate redis;
    private final ReadModelService readModelService;   // CQRS read model — replaces direct DB reads
    private final ExportService exportService;
    private final ObjectMapper objectMapper;

    public ReportingService(ReportDefinitionRepository reportRepo,
                             ReportExportRepository exportRepo,
                             StringRedisTemplate redis,
                             ReadModelService readModelService,
                             ExportService exportService,
                             ObjectMapper objectMapper) {
        this.reportRepo = reportRepo;
        this.exportRepo = exportRepo;
        this.redis = redis;
        this.readModelService = readModelService;
        this.exportService = exportService;
        this.objectMapper = objectMapper;
    }

    // ── Dashboards — read model only ──────────────────────────────────────────

    /**
     * GET /api/v1/reporting/dashboards/{module}
     * Reads exclusively from the Elasticsearch read model (CQRS read path).
     * Redis caching with 5-minute TTL is applied on top.
     */
    public Map<String, Object> getDashboard(String module) {
        UUID tenantId = TenantContext.getTenantId();
        String cacheKey = "dashboard:rm:" + tenantId + ":" + module;

        String cached = safeRedisGet(cacheKey);
        if (cached != null) {
            try {
                Map<String, Object> cachedMap = objectMapper.readValue(
                        cached, new TypeReference<Map<String, Object>>() {});
                cachedMap.put("cached", true);
                return cachedMap;
            } catch (Exception ignored) {
                // fall through to re-fetch on deserialization error
            }
        }

        // Read from Elasticsearch read model — never PostgreSQL
        Map<String, Object> metrics = readModelService.getDashboardMetrics(module, tenantId.toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("module", module);
        response.put("generatedAt", OffsetDateTime.now().toString());
        response.put("cached", false);
        response.put("readModel", "elasticsearch");
        response.put("metrics", metrics);

        try {
            String json = objectMapper.writeValueAsString(response);
            safeRedisSet(cacheKey, json, DASHBOARD_TTL_SECONDS);
        } catch (Exception ignored) {}

        return response;
    }

    // ── Report definitions (write model — PostgreSQL) ─────────────────────────

    @Transactional
    public ReportDefinition defineReport(DefineReportRequest req) {
        ReportDefinition def = new ReportDefinition();
        def.setTenantId(TenantContext.getTenantId());
        def.setName(req.name());
        def.setSourceModule(req.sourceModule());
        def.setFilterCriteria(req.filterCriteria());
        def.setGroupingFields(req.groupingFields());
        def.setOutputColumns(req.outputColumns());
        def.setCreatedBy(currentUserId());
        return reportRepo.save(def);
    }

    // ── Report execution — read model only ────────────────────────────────────

    /**
     * POST /api/v1/reporting/reports/{id}/execute
     * Executes the report against the Elasticsearch read model (CQRS read path).
     * PostgreSQL is not queried.
     */
    @Transactional(readOnly = true)
    public ReportResult executeReport(UUID reportId, String cursor, int pageSize) {
        UUID tenantId = TenantContext.getTenantId();
        ReportDefinition def = reportRepo.findByIdAndTenantId(reportId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        int page = decodeCursorPage(cursor);
        int effectivePageSize = Math.min(pageSize, 1000);

        // Build filters from report definition
        Map<String, String> filters = new LinkedHashMap<>();
        if (def.getFilterCriteria() != null && !def.getFilterCriteria().isBlank()) {
            try {
                Map<String, Object> raw = objectMapper.readValue(
                        def.getFilterCriteria(), new TypeReference<Map<String, Object>>() {});
                raw.forEach((k, v) -> filters.put(k, v != null ? v.toString() : null));
            } catch (Exception e) {
                throw new BusinessRuleException("Invalid filterCriteria JSON: " + e.getMessage());
            }
        }

        ReadModelService.ReadModelPage rmPage = readModelService.queryModule(
                def.getSourceModule(), tenantId.toString(), filters, page, effectivePageSize);

        if (rmPage.total() > SYNC_ROW_LIMIT) {
            throw new BusinessRuleException("Result set too large — use async export");
        }

        int nextPage = page + 1;
        long nextOffset = (long) nextPage * effectivePageSize;
        String nextCursor = nextOffset < rmPage.total() ? encodeCursorPage(nextPage) : null;

        return new ReportResult(rmPage.rows(), (int) rmPage.total(), rmPage.rows().size(), nextCursor);
    }

    // ── Async export ──────────────────────────────────────────────────────────

    @Transactional
    public ReportExport requestExport(UUID reportId, String format) {
        reportRepo.findByIdAndTenantId(reportId, TenantContext.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        ReportExport export = new ReportExport();
        export.setTenantId(TenantContext.getTenantId());
        export.setReportId(reportId);
        export.setFormat(format.toUpperCase());
        export.setRequestedBy(currentUserId());
        ReportExport saved = exportRepo.save(export);

        processExportAsync(saved.getId());
        return saved;
    }

    public void processExportAsync(UUID exportId) {
        exportRepo.findById(exportId).ifPresent(export ->
            exportService.processExport(
                    export.getId(),
                    export.getTenantId(),
                    export.getReportId(),
                    export.getFormat()
            )
        );
    }

    // ── Full-text search — read model only ────────────────────────────────────

    /**
     * GET /api/v1/reporting/search?q=...
     * Searches the Elasticsearch read model (CQRS read path).
     */
    public List<Map<String, Object>> search(String query) {
        return readModelService.search(query, TenantContext.getTenantId().toString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String s) {
            try { return UUID.fromString(s); } catch (IllegalArgumentException ignored) {}
        }
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    private String safeRedisGet(String key) {
        try { return redis.opsForValue().get(key); } catch (Exception e) { return null; }
    }

    private void safeRedisSet(String key, String value, long ttlSeconds) {
        try { redis.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS); } catch (Exception ignored) {}
    }

    private int decodeCursorPage(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            return Integer.parseInt(new String(Base64.getDecoder().decode(cursor)));
        } catch (Exception e) {
            return 0;
        }
    }

    private String encodeCursorPage(int page) {
        return Base64.getEncoder().encodeToString(String.valueOf(page).getBytes());
    }
}
