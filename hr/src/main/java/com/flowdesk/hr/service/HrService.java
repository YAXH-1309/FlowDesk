package com.flowdesk.hr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.audit.AuditAction;
import com.flowdesk.core.audit.AuditLog;
import com.flowdesk.core.context.TenantContext;
import com.flowdesk.core.exception.ConflictException;
import com.flowdesk.core.exception.ResourceNotFoundException;
import com.flowdesk.core.kafka.events.EmployeeChangedEvent;
import com.flowdesk.core.kafka.events.ReviewSubmittedEvent;
import com.flowdesk.core.lock.DistributedLockService;
import com.flowdesk.hr.domain.Attendance;
import com.flowdesk.hr.domain.Employee;
import com.flowdesk.hr.domain.PayrollRun;
import com.flowdesk.hr.domain.PerformanceReview;
import com.flowdesk.hr.dto.*;
import com.flowdesk.hr.outbox.HrOutboxEvent;
import com.flowdesk.hr.outbox.HrOutboxRepository;
import com.flowdesk.hr.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HrService {

    // Statutory deduction rate (20% flat for simplicity)
    private static final BigDecimal DEDUCTION_RATE = new BigDecimal("0.20");

    private final EmployeeRepository employeeRepo;
    private final AttendanceRepository attendanceRepo;
    private final PerformanceReviewRepository reviewRepo;
    private final PayrollRunRepository payrollRunRepo;
    private final HrOutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;
    private final DistributedLockService lockService;

    // In-memory payroll report cache keyed by runId
    private final Map<UUID, PayrollReport> reportCache = new ConcurrentHashMap<>();

    public HrService(EmployeeRepository employeeRepo,
                     AttendanceRepository attendanceRepo,
                     PerformanceReviewRepository reviewRepo,
                     PayrollRunRepository payrollRunRepo,
                     HrOutboxRepository outboxRepo,
                     ObjectMapper objectMapper,
                     DistributedLockService lockService) {
        this.employeeRepo = employeeRepo;
        this.attendanceRepo = attendanceRepo;
        this.reviewRepo = reviewRepo;
        this.payrollRunRepo = payrollRunRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
        this.lockService = lockService;
    }

    // ── Employees ─────────────────────────────────────────────────────────────

    @Transactional
    @AuditLog(action = AuditAction.CREATE, entityType = "Employee")
    public Employee createEmployee(CreateEmployeeRequest req) {
        Employee emp = new Employee();
        emp.setTenantId(TenantContext.getTenantId());
        emp.setFullName(req.fullName());
        emp.setDepartment(req.department());
        emp.setJobTitle(req.jobTitle());
        emp.setStartDate(req.startDate());
        emp.setBaseSalary(req.baseSalary());
        emp.setCurrency(req.currency());
        Employee saved = employeeRepo.save(emp);
        publishOutbox("Employee", saved.getId(), "hr.employee.changed", toEmployeeChangedEvent(saved));
        return saved;
    }

    @Transactional
    @AuditLog(action = AuditAction.UPDATE, entityType = "Employee")
    public Employee updateEmployee(UUID id, CreateEmployeeRequest req) {
        Employee emp = employeeRepo.findByIdAndTenantId(id, TenantContext.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        emp.setFullName(req.fullName());
        emp.setDepartment(req.department());
        emp.setJobTitle(req.jobTitle());
        emp.setStartDate(req.startDate());
        emp.setBaseSalary(req.baseSalary());
        emp.setCurrency(req.currency());
        Employee saved = employeeRepo.save(emp);
        publishOutbox("Employee", saved.getId(), "hr.employee.changed", toEmployeeChangedEvent(saved));
        return saved;
    }

    // ── Attendance ────────────────────────────────────────────────────────────

    @Transactional
    public Attendance recordAttendance(RecordAttendanceRequest req) {
        Attendance att = new Attendance();
        att.setTenantId(TenantContext.getTenantId());
        att.setEmployeeId(req.employeeId());
        att.setDate(req.date());
        att.setCheckIn(req.checkIn());
        att.setCheckOut(req.checkOut());
        att.setStatus(req.status());
        return attendanceRepo.save(att);
    }

    // ── Payroll ───────────────────────────────────────────────────────────────

    @Transactional
    public PayrollReport runPayroll(PayrollRunRequest req) {
        UUID tenantId = TenantContext.getTenantId();
        String lockKey = "lock:payroll:" + tenantId + ":" + req.payPeriodStart();
        if (!lockService.tryLock(lockKey, 30)) {
            throw new ConflictException("Payroll run already in progress for this period");
        }
        try {
            List<Employee> active = employeeRepo.findByTenantIdAndEmploymentStatus(tenantId, "ACTIVE");

        List<PayrollLineItem> lines = new ArrayList<>();
        List<UUID> skipped = new ArrayList<>();

        for (Employee emp : active) {
            if (emp.getBaseSalary() == null) {
                skipped.add(emp.getId());
                continue;
            }
            BigDecimal gross = emp.getBaseSalary();
            BigDecimal deductions = gross.multiply(DEDUCTION_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(deductions);
            lines.add(new PayrollLineItem(emp.getId(), emp.getFullName(), gross, deductions, net));
        }

        PayrollRun run = new PayrollRun();
        run.setTenantId(tenantId);
        run.setPayPeriodStart(req.payPeriodStart());
        run.setPayPeriodEnd(req.payPeriodEnd());
        PayrollRun saved = payrollRunRepo.save(run);

        PayrollReport report = new PayrollReport(saved.getId(), lines, skipped);
        reportCache.put(saved.getId(), report);
        return report;
        } finally {
            lockService.unlock(lockKey);
        }
    }

    public PayrollReport getPayrollReport(UUID runId) {
        PayrollReport report = reportCache.get(runId);
        if (report == null) throw new ResourceNotFoundException("Payroll run not found");
        return report;
    }

    // ── Performance Reviews ───────────────────────────────────────────────────

    @Transactional
    public PerformanceReview submitReview(SubmitReviewRequest req) {
        PerformanceReview review = new PerformanceReview();
        review.setTenantId(TenantContext.getTenantId());
        review.setEmployeeId(req.employeeId());
        review.setReviewerId(req.reviewerId());
        review.setReviewPeriod(req.reviewPeriod());
        review.setRating(req.rating());
        PerformanceReview saved = reviewRepo.save(review);
        publishOutbox("PerformanceReview", saved.getId(), "hr.review.submitted", toReviewSubmittedEvent(saved));
        return saved;
    }

    // ── Outbox helper ─────────────────────────────────────────────────────────

    private void publishOutbox(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        try {
            HrOutboxEvent event = new HrOutboxEvent();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            outboxRepo.save(event);
        } catch (Exception e) {
            // Log but don't fail the business transaction
        }
    }

    // ── Event schema mappers ──────────────────────────────────────────────────

    private EmployeeChangedEvent toEmployeeChangedEvent(Employee emp) {
        EmployeeChangedEvent event = new EmployeeChangedEvent();
        event.setEmployeeId(emp.getId());
        event.setTenantId(emp.getTenantId());
        event.setFullName(emp.getFullName());
        event.setDepartment(emp.getDepartment());
        event.setJobTitle(emp.getJobTitle());
        event.setEmploymentStatus(emp.getEmploymentStatus());
        event.setStartDate(emp.getStartDate());
        event.setBaseSalary(emp.getBaseSalary());
        event.setCurrency(emp.getCurrency());
        return event;
    }

    private ReviewSubmittedEvent toReviewSubmittedEvent(PerformanceReview review) {
        ReviewSubmittedEvent event = new ReviewSubmittedEvent();
        event.setReviewId(review.getId());
        event.setTenantId(review.getTenantId());
        event.setEmployeeId(review.getEmployeeId());
        event.setReviewerId(review.getReviewerId());
        event.setReviewPeriod(review.getReviewPeriod());
        event.setRating(review.getRating());
        event.setSubmittedAt(review.getSubmittedAt());
        return event;
    }
}
