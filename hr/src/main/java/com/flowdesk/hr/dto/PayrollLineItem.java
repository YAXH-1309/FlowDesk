package com.flowdesk.hr.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PayrollLineItem(
        UUID employeeId,
        String fullName,
        BigDecimal grossPay,
        BigDecimal deductions,
        BigDecimal netPay
) {}
