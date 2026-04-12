package com.flowdesk.hr.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PayrollRunRequest(
        @NotNull LocalDate payPeriodStart,
        @NotNull LocalDate payPeriodEnd
) {}
