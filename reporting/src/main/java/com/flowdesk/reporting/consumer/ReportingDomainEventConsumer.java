package com.flowdesk.reporting.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.reporting.readmodel.ReadModelDocument;
import com.flowdesk.reporting.readmodel.ReadModelService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CQRS write path: consumes domain events from all modules and updates the Elasticsearch read model.
 *
 * Topics consumed:
 *   - hr.employee.changed       → indexes employee data
 *   - inventory.low-stock       → indexes low-stock SKU alerts
 *   - sales.order.confirmed     → indexes confirmed sales orders
 *   - sales.credit-hold         → updates order status to CREDIT_HOLD
 *   - accounting.invoice.overdue → indexes overdue AR invoices
 *   - audit.events              → indexes audit trail entries
 *
 * All updates are asynchronous and eventually consistent.
 * The read model (Elasticsearch) is the sole data source for all GET /api/v1/reporting/... endpoints.
 */
@Component
public class ReportingDomainEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportingDomainEventConsumer.class);

    private final ReadModelService readModelService;
    private final ObjectMapper objectMapper;

    public ReportingDomainEventConsumer(ReadModelService readModelService, ObjectMapper objectMapper) {
        this.readModelService = readModelService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
        topics = {
            "hr.employee.changed",
            "inventory.low-stock",
            "sales.order.confirmed",
            "sales.credit-hold",
            "accounting.invoice.overdue",
            "audit.events"
        },
        groupId = "reporting-cqrs-consumer",
        containerFactory = "reportingKafkaListenerContainerFactory"
    )
    public void onDomainEvent(ConsumerRecord<String, String> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            ReadModelDocument doc = mapToReadModel(record.topic(), payload);
            if (doc != null) {
                readModelService.upsert(doc);
                log.debug("Updated read model from topic={} key={}", record.topic(), record.key());
            }
        } catch (Exception e) {
            log.error("Failed to process domain event from topic={}: {}", record.topic(), e.getMessage(), e);
            // Re-throw so Kafka retry/DLQ mechanism handles it
            throw new RuntimeException("Read model update failed for topic " + record.topic(), e);
        }
    }

    // ── Event → ReadModelDocument mapping ────────────────────────────────────

    private ReadModelDocument mapToReadModel(String topic, JsonNode payload) {
        return switch (topic) {
            case "hr.employee.changed"        -> mapEmployee(payload);
            case "inventory.low-stock"        -> mapLowStock(payload);
            case "sales.order.confirmed"      -> mapOrderConfirmed(payload);
            case "sales.credit-hold"          -> mapCreditHold(payload);
            case "accounting.invoice.overdue" -> mapOverdueInvoice(payload);
            case "audit.events"               -> mapAuditEvent(payload);
            default -> {
                log.warn("No read model mapping for topic: {}", topic);
                yield null;
            }
        };
    }

    private ReadModelDocument mapEmployee(JsonNode p) {
        String employeeId = p.path("employeeId").asText();
        String tenantId   = p.path("tenantId").asText();
        String fullName   = p.path("fullName").asText();
        String department = p.path("department").asText("");
        String jobTitle   = p.path("jobTitle").asText("");
        String status     = p.path("employmentStatus").asText(p.path("status").asText("ACTIVE"));

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("department", department);
        attrs.put("jobTitle", jobTitle);
        attrs.put("startDate", p.path("startDate").asText(""));

        ReadModelDocument doc = new ReadModelDocument();
        doc.setId("hr_employee_" + employeeId);
        doc.setModule("hr");
        doc.setEntityType("employee");
        doc.setEntityId(employeeId);
        doc.setTenantId(tenantId);
        doc.setDisplayName(fullName);
        doc.setDescription(department + " — " + jobTitle);
        doc.setStatus(status);
        doc.setAttributes(attrs);
        doc.setEventTime(Instant.now());
        return doc;
    }

    private ReadModelDocument mapLowStock(JsonNode p) {
        String skuId       = p.path("skuId").asText();
        String tenantId    = p.path("tenantId").asText();
        String productName = p.path("productName").asText();
        int    qty         = p.path("quantityOnHand").asInt(0);
        int    threshold   = p.path("reorderThreshold").asInt(0);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("quantityOnHand", qty);
        attrs.put("reorderThreshold", threshold);
        attrs.put("warehouseId", p.path("warehouseId").asText(""));

        ReadModelDocument doc = new ReadModelDocument();
        doc.setId("inventory_sku_" + skuId);
        doc.setModule("inventory");
        doc.setEntityType("sku");
        doc.setEntityId(skuId);
        doc.setTenantId(tenantId);
        doc.setDisplayName(productName);
        doc.setDescription("Low stock: " + qty + " remaining (threshold: " + threshold + ")");
        doc.setStatus("LOW_STOCK");
        doc.setAttributes(attrs);
        doc.setEventTime(Instant.now());
        return doc;
    }

    private ReadModelDocument mapOrderConfirmed(JsonNode p) {
        String orderId    = p.path("orderId").asText();
        String tenantId   = p.path("tenantId").asText();
        String customerId = p.path("customerId").asText("");

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("customerId", customerId);
        attrs.put("opportunityId", p.path("opportunityId").asText(""));
        attrs.put("confirmedAt", p.path("confirmedAt").asText(""));

        ReadModelDocument doc = new ReadModelDocument();
        doc.setId("sales_order_" + orderId);
        doc.setModule("sales");
        doc.setEntityType("order");
        doc.setEntityId(orderId);
        doc.setTenantId(tenantId);
        doc.setDisplayName("Order " + orderId);
        doc.setDescription("Confirmed order for customer " + customerId);
        doc.setStatus("CONFIRMED");
        doc.setAttributes(attrs);
        doc.setEventTime(Instant.now());
        return doc;
    }

    private ReadModelDocument mapCreditHold(JsonNode p) {
        String orderId    = p.path("orderId").asText();
        String tenantId   = p.path("tenantId").asText();
        String customerId = p.path("customerId").asText("");

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("customerId", customerId);
        attrs.put("creditLimit", p.path("creditLimit").asText(""));
        attrs.put("outstandingBalance", p.path("outstandingBalance").asText(""));

        ReadModelDocument doc = new ReadModelDocument();
        doc.setId("sales_order_" + orderId);  // same ID as confirmed order — upsert updates status
        doc.setModule("sales");
        doc.setEntityType("order");
        doc.setEntityId(orderId);
        doc.setTenantId(tenantId);
        doc.setDisplayName("Order " + orderId);
        doc.setDescription("Order on credit hold for customer " + customerId);
        doc.setStatus("CREDIT_HOLD");
        doc.setAttributes(attrs);
        doc.setEventTime(Instant.now());
        return doc;
    }

    private ReadModelDocument mapOverdueInvoice(JsonNode p) {
        String invoiceId  = p.path("invoiceId").asText();
        String tenantId   = p.path("tenantId").asText();
        String customerId = p.path("customerId").asText("");
        String amountDue  = p.path("amountDue").asText("0");

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("customerId", customerId);
        attrs.put("amountDue", amountDue);
        attrs.put("dueDate", p.path("dueDate").asText(""));

        ReadModelDocument doc = new ReadModelDocument();
        doc.setId("accounting_invoice_" + invoiceId);
        doc.setModule("accounting");
        doc.setEntityType("invoice");
        doc.setEntityId(invoiceId);
        doc.setTenantId(tenantId);
        doc.setDisplayName("Overdue Invoice " + invoiceId);
        doc.setDescription("Overdue AR invoice — amount due: " + amountDue);
        doc.setStatus("OVERDUE");
        doc.setAttributes(attrs);
        doc.setEventTime(Instant.now());
        return doc;
    }

    private ReadModelDocument mapAuditEvent(JsonNode p) {
        String entityId   = p.path("entityId").asText();
        String tenantId   = p.path("tenantId").asText();
        String entityType = p.path("entityType").asText("unknown");
        String action     = p.path("action").asText("");
        String actorId    = p.path("actorId").asText("");

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("actorId", actorId);
        attrs.put("action", action);
        attrs.put("timestamp", p.path("timestamp").asText(""));

        ReadModelDocument doc = new ReadModelDocument();
        doc.setId("audit_" + entityType + "_" + entityId + "_" + System.currentTimeMillis());
        doc.setModule("audit");
        doc.setEntityType(entityType);
        doc.setEntityId(entityId);
        doc.setTenantId(tenantId);
        doc.setDisplayName(action + " on " + entityType + " " + entityId);
        doc.setDescription("Actor: " + actorId + " performed " + action);
        doc.setStatus(action);
        doc.setAttributes(attrs);
        doc.setEventTime(Instant.now());
        return doc;
    }
}
