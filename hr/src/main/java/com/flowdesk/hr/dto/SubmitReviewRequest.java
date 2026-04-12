package com.flowdesk.hr.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record SubmitReviewRequest(
        @NotNull UUID employeeId,
        @NotNull UUID reviewerId,
        @NotBlank String reviewPeriod,
        @NotNull @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal rating
) {}
