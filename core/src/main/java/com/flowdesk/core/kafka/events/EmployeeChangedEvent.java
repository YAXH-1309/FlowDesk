package com.flowdesk.core.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published to {@code hr.employee.changed} when an employee record is created or updated.
 * Schema version: 1
 */
public class EmployeeChangedEvent extends KafkaEvent {

    @JsonProperty("employeeId")
    private UUID employeeId;

    @JsonProperty("tenantId")
    private UUID tenantId;

    @JsonProperty("fullName")
    private String fullName;

    @JsonProperty("department")
    private String department;

    @JsonProperty("jobTitle")
    private String jobTitle;

    @JsonProperty("employmentStatus")
    private String employmentStatus;

    @JsonProperty("startDate")
    private LocalDate startDate;

    @JsonProperty("baseSalary")
    private BigDecimal baseSalary;

    @JsonProperty("currency")
    private String currency;

    public EmployeeChangedEvent() {}

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
