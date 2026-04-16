package com.flowdesk.core.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published to {@code sales.order.confirmed} when a sales order is confirmed.
 * Schema version: 1
 */
public class OrderConfirmedEvent extends KafkaEvent {

    @JsonProperty("orderId")
    private UUID orderId;

    @JsonProperty("tenantId")
    private UUID tenantId;

    @JsonProperty("customerId")
    private UUID customerId;

    @JsonProperty("opportunityId")
    private UUID opportunityId;

    @JsonProperty("totalAmount")
    private BigDecimal totalAmount;

    @JsonProperty("status")
    private String status;

    public OrderConfirmedEvent() {}

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public UUID getOpportunityId() { return opportunityId; }
    public void setOpportunityId(UUID opportunityId) { this.opportunityId = opportunityId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
