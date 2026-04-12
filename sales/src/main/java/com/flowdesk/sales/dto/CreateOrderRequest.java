package com.flowdesk.sales.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        UUID opportunityId,
        BigDecimal totalAmount
) {}
