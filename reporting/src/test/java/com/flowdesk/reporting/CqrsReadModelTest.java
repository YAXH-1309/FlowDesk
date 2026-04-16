package com.flowdesk.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowdesk.reporting.consumer.ReportingDomainEventConsumer;
import com.flowdesk.reporting.readmodel.ReadModelDocument;
import com.flowdesk.reporting.readmodel.ReadModelService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the CQRS read model consumer.
 * Verifies that domain events from each Kafka topic are correctly mapped
 * to ReadModelDocument and upserted into the read model.
 */
@ExtendWith(MockitoExtension.class)
class CqrsReadModelTest {

    @Mock
    private ReadModelService readModelService;

    private ReportingDomainEventConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new ReportingDomainEventConsumer(readModelService, objectMapper);
    }

    // ── hr.employee.changed ───────────────────────────────────────────────────

    @Test
    void employeeChangedEvent_indexesEmployeeInReadModel() throws Exception {
        Map<String, Object> payload = Map.of(
                "employeeId", "emp-001",
                "tenantId", "tenant-abc",
                "fullName", "Alice Smith",
                "department", "Engineering",
                "jobTitle", "Senior Engineer",
                "employmentStatus", "ACTIVE"
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "hr.employee.changed", 0, 0L, "emp-001", objectMapper.writeValueAsString(payload));

        consumer.onDomainEvent(record);

        ArgumentCaptor<ReadModelDocument> captor = ArgumentCaptor.forClass(ReadModelDocument.class);
        verify(readModelService).upsert(captor.capture());

        ReadModelDocument doc = captor.getValue();
        assertThat(doc.getId()).isEqualTo("hr_employee_emp-001");
        assertThat(doc.getModule()).isEqualTo("hr");
        assertThat(doc.getEntityType()).isEqualTo("employee");
        assertThat(doc.getEntityId()).isEqualTo("emp-001");
        assertThat(doc.getTenantId()).isEqualTo("tenant-abc");
        assertThat(doc.getDisplayName()).isEqualTo("Alice Smith");
        assertThat(doc.getStatus()).isEqualTo("ACTIVE");
        assertThat(doc.getAttributes()).containsEntry("department", "Engineering");
        assertThat(doc.getAttributes()).containsEntry("jobTitle", "Senior Engineer");
    }

    // ── inventory.low-stock ───────────────────────────────────────────────────

    @Test
    void lowStockEvent_indexesSkuWithLowStockStatus() throws Exception {
        Map<String, Object> payload = Map.of(
                "skuId", "sku-999",
                "tenantId", "tenant-abc",
                "productName", "Widget Pro",
                "quantityOnHand", 3,
                "reorderThreshold", 10
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "inventory.low-stock", 0, 0L, "sku-999", objectMapper.writeValueAsString(payload));

        consumer.onDomainEvent(record);

        ArgumentCaptor<ReadModelDocument> captor = ArgumentCaptor.forClass(ReadModelDocument.class);
        verify(readModelService).upsert(captor.capture());

        ReadModelDocument doc = captor.getValue();
        assertThat(doc.getId()).isEqualTo("inventory_sku_sku-999");
        assertThat(doc.getModule()).isEqualTo("inventory");
        assertThat(doc.getEntityType()).isEqualTo("sku");
        assertThat(doc.getStatus()).isEqualTo("LOW_STOCK");
        assertThat(doc.getDisplayName()).isEqualTo("Widget Pro");
        assertThat(doc.getAttributes()).containsEntry("quantityOnHand", 3);
    }

    // ── sales.order.confirmed ─────────────────────────────────────────────────

    @Test
    void orderConfirmedEvent_indexesOrderWithConfirmedStatus() throws Exception {
        Map<String, Object> payload = Map.of(
                "orderId", "order-42",
                "tenantId", "tenant-abc",
                "customerId", "cust-7"
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "sales.order.confirmed", 0, 0L, "order-42", objectMapper.writeValueAsString(payload));

        consumer.onDomainEvent(record);

        ArgumentCaptor<ReadModelDocument> captor = ArgumentCaptor.forClass(ReadModelDocument.class);
        verify(readModelService).upsert(captor.capture());

        ReadModelDocument doc = captor.getValue();
        assertThat(doc.getId()).isEqualTo("sales_order_order-42");
        assertThat(doc.getModule()).isEqualTo("sales");
        assertThat(doc.getEntityType()).isEqualTo("order");
        assertThat(doc.getStatus()).isEqualTo("CONFIRMED");
    }

    // ── sales.credit-hold ─────────────────────────────────────────────────────

    @Test
    void creditHoldEvent_updatesOrderStatusToCreditHold() throws Exception {
        Map<String, Object> payload = Map.of(
                "orderId", "order-42",
                "tenantId", "tenant-abc",
                "customerId", "cust-7",
                "creditLimit", "5000.00",
                "outstandingBalance", "6000.00"
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "sales.credit-hold", 0, 0L, "order-42", objectMapper.writeValueAsString(payload));

        consumer.onDomainEvent(record);

        ArgumentCaptor<ReadModelDocument> captor = ArgumentCaptor.forClass(ReadModelDocument.class);
        verify(readModelService).upsert(captor.capture());

        ReadModelDocument doc = captor.getValue();
        // Same document ID as confirmed order — upsert overwrites status
        assertThat(doc.getId()).isEqualTo("sales_order_order-42");
        assertThat(doc.getStatus()).isEqualTo("CREDIT_HOLD");
        assertThat(doc.getAttributes()).containsEntry("creditLimit", "5000.00");
    }

    // ── accounting.invoice.overdue ────────────────────────────────────────────

    @Test
    void overdueInvoiceEvent_indexesInvoiceWithOverdueStatus() throws Exception {
        Map<String, Object> payload = Map.of(
                "invoiceId", "inv-100",
                "tenantId", "tenant-abc",
                "customerId", "cust-7",
                "amountDue", "1500.00",
                "dueDate", "2024-01-01"
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "accounting.invoice.overdue", 0, 0L, "inv-100", objectMapper.writeValueAsString(payload));

        consumer.onDomainEvent(record);

        ArgumentCaptor<ReadModelDocument> captor = ArgumentCaptor.forClass(ReadModelDocument.class);
        verify(readModelService).upsert(captor.capture());

        ReadModelDocument doc = captor.getValue();
        assertThat(doc.getId()).isEqualTo("accounting_invoice_inv-100");
        assertThat(doc.getModule()).isEqualTo("accounting");
        assertThat(doc.getEntityType()).isEqualTo("invoice");
        assertThat(doc.getStatus()).isEqualTo("OVERDUE");
        assertThat(doc.getAttributes()).containsEntry("amountDue", "1500.00");
    }

    // ── audit.events ──────────────────────────────────────────────────────────

    @Test
    void auditEvent_indexesAuditEntry() throws Exception {
        Map<String, Object> payload = Map.of(
                "entityId", "emp-001",
                "tenantId", "tenant-abc",
                "entityType", "employee",
                "action", "UPDATE",
                "actorId", "user-xyz"
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "audit.events", 0, 0L, "emp-001", objectMapper.writeValueAsString(payload));

        consumer.onDomainEvent(record);

        ArgumentCaptor<ReadModelDocument> captor = ArgumentCaptor.forClass(ReadModelDocument.class);
        verify(readModelService).upsert(captor.capture());

        ReadModelDocument doc = captor.getValue();
        assertThat(doc.getModule()).isEqualTo("audit");
        assertThat(doc.getEntityType()).isEqualTo("employee");
        assertThat(doc.getStatus()).isEqualTo("UPDATE");
        assertThat(doc.getAttributes()).containsEntry("actorId", "user-xyz");
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void malformedPayload_throwsRuntimeException() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "hr.employee.changed", 0, 0L, "key", "not-valid-json{{{");

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> consumer.onDomainEvent(record));

        verify(readModelService, never()).upsert(any());
    }

    @Test
    void unknownTopic_doesNotUpsert() throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "unknown.topic", 0, 0L, "key", "{\"id\":\"x\"}");

        // Should not throw, but also should not upsert (null doc returned)
        // The consumer logs a warning and skips
        // We need to verify no upsert happens — but the consumer will throw because
        // the switch returns null and we don't call upsert for null docs.
        // Actually the consumer only calls upsert if doc != null.
        // Since "unknown.topic" is not in the @KafkaListener topics list, this is
        // a defensive test for the mapToReadModel null path.
        consumer.onDomainEvent(record);
        verify(readModelService, never()).upsert(any());
    }
}
