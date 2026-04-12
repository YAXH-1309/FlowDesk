package com.flowdesk.hr.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance", schema = "hr_schema")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "check_in")
    private OffsetDateTime checkIn;

    @Column(name = "check_out")
    private OffsetDateTime checkOut;

    @Column(nullable = false, length = 20)
    private String status;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public OffsetDateTime getCheckIn() { return checkIn; }
    public void setCheckIn(OffsetDateTime checkIn) { this.checkIn = checkIn; }
    public OffsetDateTime getCheckOut() { return checkOut; }
    public void setCheckOut(OffsetDateTime checkOut) { this.checkOut = checkOut; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
