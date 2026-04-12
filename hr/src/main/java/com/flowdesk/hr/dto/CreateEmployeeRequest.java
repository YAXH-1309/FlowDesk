package com.flowdesk.hr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateEmployeeRequest(
        @NotBlank String fullName,
        String department,
        String jobTitle,
        @NotNull LocalDate startDate,
        BigDecimal baseSalary,
        String currency
) {}
