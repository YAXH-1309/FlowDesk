package com.flowdesk.reporting.controller;

import com.flowdesk.reporting.domain.ReportDefinition;
import com.flowdesk.reporting.domain.ReportExport;
import com.flowdesk.reporting.dto.DefineReportRequest;
import com.flowdesk.reporting.dto.ReportResult;
import com.flowdesk.reporting.service.ReportingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public ReportDefinition defineReport(@Valid @RequestBody DefineReportRequest req) {
        return reportingService.defineReport(req);
    }

    @PostMapping("/reports/{id}/execute")
    public ReportResult executeReport(@PathVariable UUID id,
                                       @RequestParam(required = false) String cursor) {
        return reportingService.executeReport(id, cursor);
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
