package com.flowdesk.hr.controller;

import com.flowdesk.core.idempotency.IdempotencyRequired;
import com.flowdesk.hr.domain.Attendance;
import com.flowdesk.hr.domain.Employee;
import com.flowdesk.hr.domain.PerformanceReview;
import com.flowdesk.hr.dto.*;
import com.flowdesk.hr.service.HrService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr")
public class HrController {

    private final HrService hrService;

    public HrController(HrService hrService) {
        this.hrService = hrService;
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public Employee createEmployee(@Valid @RequestBody CreateEmployeeRequest req) {
        return hrService.createEmployee(req);
    }

    @PutMapping("/employees/{id}")
    public Employee updateEmployee(@PathVariable UUID id,
                                   @Valid @RequestBody CreateEmployeeRequest req) {
        return hrService.updateEmployee(id, req);
    }

    @PostMapping("/attendance")
    @ResponseStatus(HttpStatus.CREATED)
    public Attendance recordAttendance(@Valid @RequestBody RecordAttendanceRequest req) {
        return hrService.recordAttendance(req);
    }

    @PostMapping("/payroll/run")
    @ResponseStatus(HttpStatus.CREATED)
    @IdempotencyRequired
    public PayrollReport runPayroll(@Valid @RequestBody PayrollRunRequest req) {
        return hrService.runPayroll(req);
    }

    @GetMapping("/payroll/{runId}/report")
    public PayrollReport getPayrollReport(@PathVariable UUID runId) {
        return hrService.getPayrollReport(runId);
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public PerformanceReview submitReview(@Valid @RequestBody SubmitReviewRequest req) {
        return hrService.submitReview(req);
    }
}
