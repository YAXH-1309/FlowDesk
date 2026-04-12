package com.flowdesk.reporting.service;

import com.flowdesk.core.context.TenantContext;
import com.flowdesk.core.exception.BusinessRuleException;
import com.flowdesk.core.exception.ResourceNotFoundException;
import com.flowdesk.reporting.domain.ReportDefinition;
import com.flowdesk.reporting.domain.ReportExport;
import com.flowdesk.reporting.dto.DefineReportRequest;
import com.flowdesk.reporting.dto.ReportResult;
import com.flowdesk.reporting.repository.ReportDefinitionRepository;
import com.flowdesk.reporting.repository.ReportExportRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ReportingService {

    private static final int SYNC_ROW_LIMIT = 10_000;
    private static final int PAGE_SIZE = 1_000;
    private static final long DASHBOARD_TTL_SECONDS = 300; // 5 min

    private final ReportDefinitionRepository reportRepo;
    private final ReportExportRepository exportRepo;
    private final StringRedisTemplate redis;

    public ReportingService(ReportDefinitionRepository reportRepo,
                             ReportExportRepository exportRepo,
                             StringRedisTemplate redis) {
        this.reportRepo = reportRepo;
        this.exportRepo = exportRepo;
        this.redis = redis;
    }

    // ── Dashboards ────────────────────────────────────────────────────────────

    public Map<String, Object> getDashboard(String module) {
        String cacheKey = "dashboard:" + TenantContext.getTenantId() + ":" + module;
        String cached = safeRedisGet(cacheKey);
        if (cached != null) {
            return Map.of("module", module, "cached", true, "data", cached);
        }
        // Stub: real impl queries module-specific metrics
        Map<String, Object> metrics = Map.of("module", module, "generatedAt",
                java.time.OffsetDateTime.now().toString());
        safeRedisSet(cacheKey, metrics.toString(), DASHBOARD_TTL_SECONDS);
        return metrics;
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
    public ReportResult executeReport(UUID reportId, String cursor) {
        reportRepo.findByIdAndTenantId(reportId, TenantContext.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        // Stub result set — real impl queries the source module's read replica
        int offset = cursor != null ? Integer.parseInt(cursor) : 0;
        int totalRows = 500; // stub

        if (totalRows > SYNC_ROW_LIMIT) {
            throw new BusinessRuleException("Result set too large — use async export");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int end = Math.min(offset + PAGE_SIZE, totalRows);
        for (int i = offset; i < end; i++) {
            rows.add(Map.of("row", i));
        }

        String nextCursor = end < totalRows ? String.valueOf(end) : null;
        return new ReportResult(rows, totalRows, nextCursor);
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

    @Async
    public void processExportAsync(UUID exportId) {
        exportRepo.findById(exportId).ifPresent(export -> {
            // Stub: real impl generates CSV/XLSX and uploads to S3
            export.setStatus("READY");
            export.setS3Key("exports/" + exportId + "." + export.getFormat().toLowerCase());
            export.setCompletedAt(java.time.OffsetDateTime.now());
            exportRepo.save(export);
        });
    }

    // ── Full-text search ──────────────────────────────────────────────────────

    public List<Map<String, Object>> search(String query) {
        // Stub: real impl queries Elasticsearch
        return List.of(Map.of("query", query, "results", List.of()));
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
