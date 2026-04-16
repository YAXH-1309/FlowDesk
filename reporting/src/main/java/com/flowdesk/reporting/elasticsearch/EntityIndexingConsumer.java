package com.flowdesk.reporting.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EntityIndexingConsumer {

    private static final Logger log = LoggerFactory.getLogger(EntityIndexingConsumer.class);

    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    public EntityIndexingConsumer(SearchService searchService, ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"hr.employee.changed", "sales.order.confirmed",
                              "inventory.low-stock", "accounting.invoice.overdue"},
                   groupId = "reporting-indexer")
    public void onEntityChanged(ConsumerRecord<String, String> record) {
        try {
            JsonNode payload = objectMapper.readTree(record.value());
            SearchDocument doc = mapToDocument(record.topic(), payload);
            if (doc != null) {
                searchService.indexDocument(doc);
            }
        } catch (Exception e) {
            log.error("Failed to index entity from topic {}: {}", record.topic(), e.getMessage(), e);
        }
    }

    private SearchDocument mapToDocument(String topic, JsonNode payload) {
        SearchDocument doc = new SearchDocument();
        doc.setUpdatedAt(Instant.now());

        switch (topic) {
            case "hr.employee.changed" -> {
                String entityId = payload.path("employeeId").asText();
                String tenantId = payload.path("tenantId").asText();
                String fullName = payload.path("fullName").asText();
                String department = payload.path("department").asText("");
                String jobTitle = payload.path("jobTitle").asText("");
                doc.setId("hr_" + entityId);
                doc.setModule("hr");
                doc.setEntityType("employee");
                doc.setTenantId(tenantId);
                doc.setTitle(fullName);
                doc.setDescription(department + " " + jobTitle);
                doc.setStatus(payload.path("status").asText(""));
            }
            case "sales.order.confirmed" -> {
                String orderId = payload.path("orderId").asText();
                String tenantId = payload.path("tenantId").asText();
                String status = payload.path("status").asText("");
                doc.setId("sales_" + orderId);
                doc.setModule("sales");
                doc.setEntityType("order");
                doc.setTenantId(tenantId);
                doc.setTitle("Order " + orderId);
                doc.setDescription(status);
                doc.setStatus(status);
            }
            case "inventory.low-stock" -> {
                String skuId = payload.path("skuId").asText();
                String tenantId = payload.path("tenantId").asText();
                String productName = payload.path("productName").asText();
                doc.setId("inventory_" + skuId);
                doc.setModule("inventory");
                doc.setEntityType("sku");
                doc.setTenantId(tenantId);
                doc.setTitle(productName);
                doc.setDescription("Low stock alert");
                doc.setStatus(payload.path("status").asText(""));
            }
            case "accounting.invoice.overdue" -> {
                String invoiceId = payload.path("invoiceId").asText();
                String tenantId = payload.path("tenantId").asText();
                String status = payload.path("status").asText("");
                doc.setId("accounting_" + invoiceId);
                doc.setModule("accounting");
                doc.setEntityType("invoice");
                doc.setTenantId(tenantId);
                doc.setTitle("Overdue invoice " + invoiceId);
                doc.setDescription(status);
                doc.setStatus(status);
            }
            default -> {
                log.warn("Unknown topic for indexing: {}", topic);
                return null;
            }
        }

        return doc;
    }
}
