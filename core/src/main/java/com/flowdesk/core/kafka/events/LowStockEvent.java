package com.flowdesk.core.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Published to {@code inventory.low-stock} when stock quantity falls at or below the reorder threshold.
 * Schema version: 1
 */
public class LowStockEvent extends KafkaEvent {

    @JsonProperty("skuId")
    private UUID skuId;

    @JsonProperty("tenantId")
    private UUID tenantId;

    @JsonProperty("warehouseId")
    private UUID warehouseId;

    @JsonProperty("quantityOnHand")
    private int quantityOnHand;

    @JsonProperty("reorderThreshold")
    private int reorderThreshold;

    public LowStockEvent() {}

    public UUID getSkuId() { return skuId; }
    public void setSkuId(UUID skuId) { this.skuId = skuId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getWarehouseId() { return warehouseId; }
    public void setWarehouseId(UUID warehouseId) { this.warehouseId = warehouseId; }

    public int getQuantityOnHand() { return quantityOnHand; }
    public void setQuantityOnHand(int quantityOnHand) { this.quantityOnHand = quantityOnHand; }

    public int getReorderThreshold() { return reorderThreshold; }
    public void setReorderThreshold(int reorderThreshold) { this.reorderThreshold = reorderThreshold; }
}
