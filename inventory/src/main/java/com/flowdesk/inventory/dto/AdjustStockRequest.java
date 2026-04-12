package com.flowdesk.inventory.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AdjustStockRequest(
        @NotNull UUID warehouseId,
        int quantityDelta
) {}
