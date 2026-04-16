package com.flowdesk.core.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published to {@code sales.credit-hold} when a sales order is placed on credit hold.
 * Schema version: 1
 */
public class CreditHoldEvent extends KafkaEvent {

    @JsonProperty("orderId")
    private UUID orderId;

    @JsonProperty("tenantId")
    private UUID tenantId;

    @JsonProperty("customerId")
    private UUID customerId;

    @JsonProperty("totalAmount")
    private BigDecimal totalAmount;

    @JsonProperty("creditHold")
    private boolean creditHold;

    public CreditHoldEvent() {}

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public boolean isCreditHold() { return creditHold; }
    public void setCreditHold(boolean creditHold) { this.creditHold = creditHold; }
}
