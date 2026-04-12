package com.flowdesk.accounting.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CreateBudgetRequest(
        @NotBlank String costCenter,
        @NotBlank String fiscalPeriod,
        BigDecimal allocated,
        BigDecimal committed,
        BigDecimal actualSpend
) {}
