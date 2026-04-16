package com.flowdesk.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.reporting.domain.ReportExport;
import com.flowdesk.reporting.dto.ReportResult;
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

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final String EXPORT_TOPIC = "reporting.export.ready";

    private final ReportExecutionService reportExecutionService;
    private final ReportExportRepository exportRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ExportService(ReportExecutionService reportExecutionService,
                         ReportExportRepository exportRepo,
                         KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper) {
        this.reportExecutionService = reportExecutionService;
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
            // 1. Fetch all rows by paginating through all pages
            List<Map<String, Object>> allRows = new ArrayList<>();
            String cursor = null;
            do {
                ReportResult page = reportExecutionService.execute(reportId, tenantId, cursor, ReportExecutionService.PAGE_SIZE);
                allRows.addAll(page.rows());
                cursor = page.nextCursor();
            } while (cursor != null);

            // 2. Generate file bytes
            byte[] data;
            if ("XLSX".equalsIgnoreCase(format)) {
                log.info("XLSX generation stub: would generate XLSX for export {}", exportId);
                data = new byte[0];
            } else {
                data = generateCsv(allRows);
            }

            // 3. Upload to S3 (stub)
            String s3Key = uploadToS3(exportId, format, data);

            // 4. Update export status
            export.setStatus("READY");
            export.setS3Key(s3Key);
            export.setCompletedAt(OffsetDateTime.now());
            exportRepo.save(export);

            // 5. Publish Kafka event
            publishExportReadyEvent(exportId, tenantId, reportId, s3Key, export.getRequestedBy());

        } catch (Exception e) {
            log.error("Failed to process export {}: {}", exportId, e.getMessage(), e);
            export.setStatus("FAILED");
            exportRepo.save(export);
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
