package com.flowdesk.inventory.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreatePurchaseOrderRequest(
        @NotNull UUID supplierId,
        @NotEmpty List<LineItemRequest> lineItems
) {
    public record LineItemRequest(
            @NotNull UUID skuId,
            int quantity,
            @NotNull BigDecimal unitCost
    ) {}
}
