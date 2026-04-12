package com.flowdesk.hr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.core.context.TenantContext;
import com.flowdesk.hr.domain.Employee;
import com.flowdesk.hr.dto.*;
import com.flowdesk.hr.outbox.HrOutboxRepository;
import com.flowdesk.hr.repository.*;
import com.flowdesk.hr.service.HrService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P20 (task 10.4): Payroll calculation correctness
 * Validates: Requirements 5.4
 */
class HrPropertyTest {

    private static final UUID TENANT = UUID.randomUUID();

    @AfterEach
    void cleanup() { TenantContext.clear(); }

    private HrService buildService(EmployeeRepository empRepo) {
        return new HrService(empRepo, mock(AttendanceRepository.class),
                mock(PerformanceReviewRepository.class), mock(PayrollRunRepository.class),
                mock(HrOutboxRepository.class), new ObjectMapper());
    }

    // ── Generators ────────────────────────────────────────────────────────────

    @Provide
    Arbitrary<BigDecimal> salaries() {
        return Arbitraries.integers().between(1_000, 100_000)
                .map(BigDecimal::new);
    }

    @Provide
    Arbitrary<List<BigDecimal>> employeeSalaries() {
        return salaries().list().ofMinSize(1).ofMaxSize(10);
    }

    // ── P20a: net pay = gross pay − deductions for every employee ─────────────

    @Property(tries = 100)
    @Tag("Feature: saas-platform, Property 20: Payroll calculation correctness")
    void p20_netPayEqualsGrossMinusDeductions(@ForAll("employeeSalaries") List<BigDecimal> salaries) {
        EmployeeRepository empRepo = mock(EmployeeRepository.class);
        PayrollRunRepository runRepo = mock(PayrollRunRepository.class);

        List<Employee> employees = new ArrayList<>();
        for (BigDecimal salary : salaries) {
            Employee emp = new Employee();
            setId(emp, UUID.randomUUID());
            emp.setTenantId(TENANT);
            emp.setFullName("Employee " + salary);
            emp.setStartDate(LocalDate.now().minusYears(1));
            emp.setBaseSalary(salary);
            emp.setEmploymentStatus("ACTIVE");
            employees.add(emp);
        }

        when(empRepo.findByTenantIdAndEmploymentStatus(TENANT, "ACTIVE")).thenReturn(employees);
        when(runRepo.save(any())).thenAnswer(inv -> {
            var run = (com.flowdesk.hr.domain.PayrollRun) inv.getArgument(0);
            setId(run, UUID.randomUUID());
            return run;
        });

        // Inject runRepo via reflection since HrService uses it internally
        HrService service = new HrService(empRepo, mock(AttendanceRepository.class),
                mock(PerformanceReviewRepository.class), runRepo,
                mock(HrOutboxRepository.class), new ObjectMapper());

        TenantContext.setTenantId(TENANT);
        PayrollReport report = service.runPayroll(
                new PayrollRunRequest(LocalDate.now().withDayOfMonth(1), LocalDate.now()));

        assertThat(report.lines()).hasSize(salaries.size());
        assertThat(report.skippedEmployeeIds()).isEmpty();

        for (PayrollLineItem line : report.lines()) {
            // net = gross - deductions
            BigDecimal expected = line.grossPay().subtract(line.deductions());
            assertThat(line.netPay())
                    .as("net pay must equal gross - deductions for %s", line.employeeId())
                    .isEqualByComparingTo(expected);

            // deductions must be positive
            assertThat(line.deductions()).isGreaterThan(BigDecimal.ZERO);

            // net must be less than gross
            assertThat(line.netPay()).isLessThan(line.grossPay());
        }
    }

    // ── P20b: Employees with missing salary are skipped ───────────────────────

    @Property(tries = 50)
    @Tag("Feature: saas-platform, Property 20: Payroll calculation correctness")
    void p20_employeesWithMissingSalaryAreSkipped(@ForAll @IntRange(min = 1, max = 5) int missingCount,
                                                   @ForAll("employeeSalaries") List<BigDecimal> salaries) {
        EmployeeRepository empRepo = mock(EmployeeRepository.class);
        PayrollRunRepository runRepo = mock(PayrollRunRepository.class);

        List<Employee> employees = new ArrayList<>();

        // Add employees with salary
        for (BigDecimal salary : salaries) {
            Employee emp = new Employee();
            setId(emp, UUID.randomUUID());
            emp.setTenantId(TENANT);
            emp.setFullName("Paid");
            emp.setStartDate(LocalDate.now().minusYears(1));
            emp.setBaseSalary(salary);
            emp.setEmploymentStatus("ACTIVE");
            employees.add(emp);
        }

        // Add employees without salary
        for (int i = 0; i < missingCount; i++) {
            Employee emp = new Employee();
            setId(emp, UUID.randomUUID());
            emp.setTenantId(TENANT);
            emp.setFullName("Unpaid " + i);
            emp.setStartDate(LocalDate.now().minusYears(1));
            emp.setBaseSalary(null); // missing
            emp.setEmploymentStatus("ACTIVE");
            employees.add(emp);
        }

        when(empRepo.findByTenantIdAndEmploymentStatus(TENANT, "ACTIVE")).thenReturn(employees);
        when(runRepo.save(any())).thenAnswer(inv -> {
            var run = (com.flowdesk.hr.domain.PayrollRun) inv.getArgument(0);
            setId(run, UUID.randomUUID());
            return run;
        });

        HrService service = new HrService(empRepo, mock(AttendanceRepository.class),
                mock(PerformanceReviewRepository.class), runRepo,
                mock(HrOutboxRepository.class), new ObjectMapper());

        TenantContext.setTenantId(TENANT);
        PayrollReport report = service.runPayroll(
                new PayrollRunRequest(LocalDate.now().withDayOfMonth(1), LocalDate.now()));

        assertThat(report.lines()).hasSize(salaries.size());
        assertThat(report.skippedEmployeeIds()).hasSize(missingCount);
    }

    // ── P20c: Deduction rate is consistent (20%) ──────────────────────────────

    @Property(tries = 100)
    @Tag("Feature: saas-platform, Property 20: Payroll calculation correctness")
    void p20_deductionRateIsConsistent(@ForAll("salaries") BigDecimal salary) {
        EmployeeRepository empRepo = mock(EmployeeRepository.class);
        PayrollRunRepository runRepo = mock(PayrollRunRepository.class);

        Employee emp = new Employee();
        setId(emp, UUID.randomUUID());
        emp.setTenantId(TENANT);
        emp.setFullName("Test");
        emp.setStartDate(LocalDate.now().minusYears(1));
        emp.setBaseSalary(salary);
        emp.setEmploymentStatus("ACTIVE");

        when(empRepo.findByTenantIdAndEmploymentStatus(TENANT, "ACTIVE")).thenReturn(List.of(emp));
        when(runRepo.save(any())).thenAnswer(inv -> {
            var run = (com.flowdesk.hr.domain.PayrollRun) inv.getArgument(0);
            setId(run, UUID.randomUUID());
            return run;
        });

        HrService service = new HrService(empRepo, mock(AttendanceRepository.class),
                mock(PerformanceReviewRepository.class), runRepo,
                mock(HrOutboxRepository.class), new ObjectMapper());

        TenantContext.setTenantId(TENANT);
        PayrollReport report = service.runPayroll(
                new PayrollRunRequest(LocalDate.now().withDayOfMonth(1), LocalDate.now()));

        PayrollLineItem line = report.lines().get(0);

        // Deduction should be 20% of gross
        BigDecimal expectedDeduction = salary.multiply(new BigDecimal("0.20"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(line.deductions()).isEqualByComparingTo(expectedDeduction);
    }

    // ── Reflection helper ─────────────────────────────────────────────────────

    private static void setId(Object entity, UUID id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null) {
                try {
                    var f = clazz.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(entity, id);
                    return;
                } catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
