package com.flowdesk.reporting.controller;

import com.flowdesk.core.exception.BusinessRuleException;
import com.flowdesk.reporting.domain.ReportDefinition;
import com.flowdesk.reporting.domain.ReportExport;
import com.flowdesk.reporting.dto.DefineReportRequest;
import com.flowdesk.reporting.dto.ReportResult;
import com.flowdesk.reporting.service.ReportingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reporting")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/dashboards/{module}")
    public Map<String, Object> getDashboard(@PathVariable String module) {
        return reportingService.getDashboard(module);
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MEMBER','ADMIN','FINANCE','MANAGER','HR_ADMIN','SALES_REP')")
    public ReportDefinition defineReport(@Valid @RequestBody DefineReportRequest req) {
        return reportingService.defineReport(req);
    }

    @PostMapping("/reports/{id}/execute")
    @PreAuthorize("hasAnyRole('MEMBER','ADMIN','FINANCE','MANAGER','HR_ADMIN','SALES_REP')")
    public ResponseEntity<?> executeReport(@PathVariable UUID id,
                                            @RequestParam(required = false) String cursor,
                                            @RequestParam(defaultValue = "1000") int pageSize,
                                            @RequestParam(defaultValue = "CSV") String exportFormat) {
        try {
            ReportResult result = reportingService.executeReport(id, cursor, pageSize);
            return ResponseEntity.ok(result);
        } catch (BusinessRuleException e) {
            if (e.getMessage().contains("Result set too large")) {
                // Auto-route to async export
                ReportExport export = reportingService.requestExport(id, exportFormat);
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "message", "Result set too large — use async export",
                    "exportId", export.getId(),
                    "status", export.getStatus()
                ));
            }
            throw e;
        }
    }

    @GetMapping("/reports/{id}/export")
    public ReportExport requestExport(@PathVariable UUID id,
                                       @RequestParam(defaultValue = "CSV") String format) {
        return reportingService.requestExport(id, format);
    }

    @GetMapping("/search")
    public List<Map<String, Object>> search(@RequestParam String q) {
        return reportingService.search(q);
    }
}
