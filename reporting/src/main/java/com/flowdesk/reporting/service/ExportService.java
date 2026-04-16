package com.flowdesk.reporting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.reporting.domain.ReportDefinition;
import com.flowdesk.reporting.domain.ReportExport;
import com.flowdesk.reporting.readmodel.ReadModelService;
import com.flowdesk.reporting.repository.ReportDefinitionRepository;
import com.flowdesk.reporting.repository.ReportExportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Async export service.
 * Fetches all rows from the Elasticsearch read model (CQRS read path) and generates CSV/XLSX.
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final String EXPORT_TOPIC = "reporting.export.ready";
    private static final int PAGE_SIZE = 1_000;

    private final ReadModelService readModelService;
    private final ReportDefinitionRepository reportRepo;
    private final ReportExportRepository exportRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ExportService(ReadModelService readModelService,
                         ReportDefinitionRepository reportRepo,
                         ReportExportRepository exportRepo,
                         KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper) {
        this.readModelService = readModelService;
        this.reportRepo = reportRepo;
        this.exportRepo = exportRepo;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Async
    @Transactional
    public void processExport(UUID exportId, UUID tenantId, UUID reportId, String format) {
        ReportExport export = exportRepo.findById(exportId).orElse(null);
        if (export == null) {
            log.warn("Export {} not found, skipping", exportId);
            return;
        }

        try {
            // 1. Load report definition to get module + filters
            ReportDefinition def = reportRepo.findByIdAndTenantId(reportId, tenantId).orElse(null);
            if (def == null) {
                log.warn("Report definition {} not found for export {}", reportId, exportId);
                export.setStatus("FAILED");
                exportRepo.save(export);
                return;
            }

            Map<String, String> filters = parseFilters(def.getFilterCriteria());

            // 2. Fetch all rows from Elasticsearch read model (paginated)
            List<Map<String, Object>> allRows = new ArrayList<>();
            int page = 0;
            long total;
            do {
                ReadModelService.ReadModelPage rmPage = readModelService.queryModule(
                        def.getSourceModule(), tenantId.toString(), filters, page, PAGE_SIZE);
                allRows.addAll(rmPage.rows());
                total = rmPage.total();
                page++;
            } while ((long) page * PAGE_SIZE < total);

            // 3. Generate file bytes
            byte[] data;
            if ("XLSX".equalsIgnoreCase(format)) {
                log.info("XLSX generation stub: would generate XLSX for export {}", exportId);
                data = new byte[0];
            } else {
                data = generateCsv(allRows);
            }

            // 4. Upload to S3 (stub)
            String s3Key = uploadToS3(exportId, format, data);

            // 5. Update export status
            export.setStatus("READY");
            export.setS3Key(s3Key);
            export.setCompletedAt(OffsetDateTime.now());
            exportRepo.save(export);

            // 6. Publish Kafka event
            publishExportReadyEvent(exportId, tenantId, reportId, s3Key, export.getRequestedBy());

        } catch (Exception e) {
            log.error("Failed to process export {}: {}", exportId, e.getMessage(), e);
            export.setStatus("FAILED");
            exportRepo.save(export);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> parseFilters(String filterCriteria) {
        if (filterCriteria == null || filterCriteria.isBlank()) return Map.of();
        try {
            Map<String, Object> raw = objectMapper.readValue(
                    filterCriteria, new TypeReference<Map<String, Object>>() {});
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((k, v) -> result.put(k, v != null ? v.toString() : null));
            return result;
        } catch (Exception e) {
            log.warn("Could not parse filterCriteria for export: {}", e.getMessage());
            return Map.of();
        }
    }

    private byte[] generateCsv(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "".getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", rows.get(0).keySet())).append("\n");
        for (Map<String, Object> row : rows) {
            sb.append(row.values().stream()
                    .map(v -> v == null ? "" : "\"" + v.toString().replace("\"", "\"\"") + "\"")
                    .collect(Collectors.joining(","))).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String uploadToS3(UUID exportId, String format, byte[] data) {
        String key = "exports/" + exportId + "." + format.toLowerCase();
        log.info("S3 upload stub: would upload {} bytes to s3://flowdesk-exports/{}", data.length, key);
        return key;
    }

    private void publishExportReadyEvent(UUID exportId, UUID tenantId, UUID reportId,
                                          String s3Key, UUID requestedBy) {
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("exportId", exportId.toString());
            payload.put("tenantId", tenantId.toString());
            payload.put("reportId", reportId.toString());
            payload.put("s3Key", s3Key);
            payload.put("requestedBy", requestedBy != null ? requestedBy.toString() : null);
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(EXPORT_TOPIC, exportId.toString(), json);
        } catch (Exception e) {
            log.error("Failed to publish export ready event for {}: {}", exportId, e.getMessage(), e);
        }
    }
}
