package com.flowdesk.core.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published to {@code accounting.invoice.overdue} when an AR invoice becomes overdue.
 * Schema version: 1
 */
public class InvoiceOverdueEvent extends KafkaEvent {

    @JsonProperty("invoiceId")
    private UUID invoiceId;

    @JsonProperty("tenantId")
    private UUID tenantId;

    @JsonProperty("customerId")
    private UUID customerId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("dueDate")
    private LocalDate dueDate;

    @JsonProperty("status")
    private String status;

    public InvoiceOverdueEvent() {}

    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
