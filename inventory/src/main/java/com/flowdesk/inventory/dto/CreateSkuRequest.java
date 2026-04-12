package com.flowdesk.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateSkuRequest(
        @NotBlank String productName,
        @Min(0) int reorderThreshold,
        @NotNull BigDecimal unitCost
) {}
