package com.flowdesk.reporting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.context.TenantContext;
import com.flowdesk.core.exception.ResourceNotFoundException;
import com.flowdesk.reporting.domain.ReportDefinition;
import com.flowdesk.reporting.domain.ReportExport;
import com.flowdesk.reporting.dto.DefineReportRequest;
import com.flowdesk.reporting.dto.ReportResult;
import com.flowdesk.reporting.elasticsearch.SearchService;
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

    private final ReportDefinitionRepository reportRepo;
    private final ReportExportRepository exportRepo;
    private final StringRedisTemplate redis;
    private final DashboardMetricsService dashboardMetricsService;
    private final ReportExecutionService reportExecutionService;
    private final ExportService exportService;
    private final ObjectMapper objectMapper;
    private final SearchService searchService;

    public ReportingService(ReportDefinitionRepository reportRepo,
                             ReportExportRepository exportRepo,
                             StringRedisTemplate redis,
                             DashboardMetricsService dashboardMetricsService,
                             ReportExecutionService reportExecutionService,
                             ExportService exportService,
                             ObjectMapper objectMapper,
                             SearchService searchService) {
        this.reportRepo = reportRepo;
        this.exportRepo = exportRepo;
        this.redis = redis;
        this.dashboardMetricsService = dashboardMetricsService;
        this.reportExecutionService = reportExecutionService;
        this.exportService = exportService;
        this.objectMapper = objectMapper;
        this.searchService = searchService;
    }

    // ── Dashboards ────────────────────────────────────────────────────────────

    public Map<String, Object> getDashboard(String module) {
        UUID tenantId = TenantContext.getTenantId();
        String cacheKey = "dashboard:" + tenantId + ":" + module;

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

        Map<String, Object> metrics = dashboardMetricsService.getMetrics(module, tenantId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("module", module);
        response.put("generatedAt", OffsetDateTime.now().toString());
        response.put("cached", false);
        response.put("metrics", metrics);

        try {
            String json = objectMapper.writeValueAsString(response);
            safeRedisSet(cacheKey, json, DASHBOARD_TTL_SECONDS);
        } catch (Exception ignored) {}

        return response;
    }

    // ── Report definitions ────────────────────────────────────────────────────

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

    // ── Report execution with cursor pagination ───────────────────────────────

    @Transactional(readOnly = true)
    public ReportResult executeReport(UUID reportId, String cursor, int pageSize) {
        return reportExecutionService.execute(reportId, TenantContext.getTenantId(), cursor, pageSize);
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

    // ── Full-text search ──────────────────────────────────────────────────────

    public List<Map<String, Object>> search(String query) {
        return searchService.search(query, TenantContext.getTenantId());
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
}
