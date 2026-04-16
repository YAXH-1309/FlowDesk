# Kafka Event Schema Upgrade Guide

This document describes the versioning strategy for all Kafka event schemas in the Flowdesk platform and provides step-by-step upgrade paths for each event type when schema changes are needed.

---

## Versioning Strategy

Every Kafka event payload includes a top-level `version` integer field (default: `1`). Consumers use the `VersionedEventDeserializer` to route messages to the correct handler based on this field.

```json
{
  "version": 1,
  "employeeId": "...",
  ...
}
```

### Rules

1. **Backward compatibility first** — prefer additive changes (new optional fields) over breaking changes.
2. **Never remove or rename a field in the same version** — this breaks existing consumers.
3. **Increment `version` only for breaking changes** — adding a required field, changing a field type, or removing a field.
4. **Register both old and new handlers** — during a rolling deployment, both v1 and v2 consumers may be active simultaneously.
5. **Deprecate old versions** — after all consumers have migrated, remove the old handler registration (not the schema class).

### Upgrade Procedure (General)

1. Create a new event class (e.g., `EmployeeChangedEventV2`) extending `KafkaEvent` with `version = 2`.
2. Update the producer to publish the new version.
3. Register a v2 handler in `VersionedEventDeserializer` alongside the existing v1 handler.
4. Deploy consumers with both handlers active.
5. Once all consumers are on v2, remove the v1 handler registration.

---

## Event Types

### `hr.employee.changed` → `EmployeeChangedEvent`

**Current version:** 1  
**Schema fields:** `version`, `employeeId`, `tenantId`, `fullName`, `department`, `jobTitle`, `employmentStatus`, `startDate`, `baseSalary`, `currency`

**Upgrade scenarios:**

| Change | Strategy |
|---|---|
| Add optional field (e.g., `managerEmail`) | Add field to `EmployeeChangedEvent` with `@JsonProperty`. No version bump needed — consumers that don't read the field are unaffected. |
| Add required field (e.g., `costCenter`) | Create `EmployeeChangedEventV2` with `version = 2`. Register v2 handler. Migrate consumers before removing v1 handler. |
| Rename `fullName` → `displayName` | Breaking change. Create v2 with new field name. Keep v1 handler for backward compatibility during rollout. |
| Change `baseSalary` type from `BigDecimal` to `String` | Breaking change. Bump to v2. |

---

### `hr.review.submitted` → `ReviewSubmittedEvent`

**Current version:** 1  
**Schema fields:** `version`, `reviewId`, `tenantId`, `employeeId`, `reviewerId`, `reviewPeriod`, `rating`, `submittedAt`

**Upgrade scenarios:**

| Change | Strategy |
|---|---|
| Add `comments` field | Additive — no version bump. |
| Add `reviewType` as required enum | Breaking — bump to v2. |
| Change `rating` from `BigDecimal` to `int` | Breaking — bump to v2. |

---

### `inventory.low-stock` → `LowStockEvent`

**Current version:** 1  
**Schema fields:** `version`, `skuId`, `tenantId`, `warehouseId`, `quantityOnHand`, `reorderThreshold`

**Upgrade scenarios:**

| Change | Strategy |
|---|---|
| Add `productName` for display purposes | Additive — no version bump. |
| Add `supplierId` as required field | Breaking — bump to v2. |
| Rename `quantityOnHand` → `currentStock` | Breaking — bump to v2. |

---

### `sales.order.confirmed` → `OrderConfirmedEvent`

**Current version:** 1  
**Schema fields:** `version`, `orderId`, `tenantId`, `customerId`, `opportunityId`, `totalAmount`, `status`

**Upgrade scenarios:**

| Change | Strategy |
|---|---|
| Add `lineItems` array | Additive — no version bump. |
| Add `confirmedAt` timestamp | Additive — no version bump. |
| Remove `opportunityId` | Breaking — bump to v2. |
| Change `totalAmount` precision | Breaking — bump to v2. |

---

### `sales.credit-hold` → `CreditHoldEvent`

**Current version:** 1  
**Schema fields:** `version`, `orderId`, `tenantId`, `customerId`, `totalAmount`, `creditHold`

**Upgrade scenarios:**

| Change | Strategy |
|---|---|
| Add `creditLimitExceededBy` amount | Additive — no version bump. |
| Add `holdReason` enum | Additive — no version bump. |
| Remove `creditHold` boolean (always true by definition) | Breaking — bump to v2. |

---

### `accounting.invoice.overdue` → `InvoiceOverdueEvent`

**Current version:** 1  
**Schema fields:** `version`, `invoiceId`, `tenantId`, `customerId`, `amount`, `dueDate`, `status`

**Upgrade scenarios:**

| Change | Strategy |
|---|---|
| Add `daysOverdue` computed field | Additive — no version bump. |
| Add `collectionAgentId` | Additive — no version bump. |
| Change `dueDate` from `LocalDate` to `OffsetDateTime` | Breaking — bump to v2. |

---

### `audit.events` → `AuditEvent`

**Current version:** 1  
**Schema fields:** `version`, `auditId`, `tenantId`, `actorId`, `action`, `entityType`, `entityId`, `timestamp`, `beforeSnapshot`, `afterSnapshot`

**Upgrade scenarios:**

| Change | Strategy |
|---|---|
| Add `ipAddress` for compliance | Additive — no version bump. |
| Add `correlationId` | Additive — no version bump. |
| Change `beforeSnapshot`/`afterSnapshot` from `String` (JSON) to structured object | Breaking — bump to v2. |
| Add `sessionId` as required field | Breaking — bump to v2. |

---

## Example: Upgrading `EmployeeChangedEvent` to v2

### Step 1 — Create the v2 schema class

```java
// core/src/main/java/com/flowdesk/core/kafka/events/EmployeeChangedEventV2.java
public class EmployeeChangedEventV2 extends KafkaEvent {

    public EmployeeChangedEventV2() {
        super(2); // version = 2
    }

    // All v1 fields plus:
    private String costCenter; // new required field
    // ...
}
```

### Step 2 — Update the producer (HrService)

```java
// In HrService.toEmployeeChangedEvent(), switch to v2:
private EmployeeChangedEventV2 toEmployeeChangedEvent(Employee emp) {
    EmployeeChangedEventV2 event = new EmployeeChangedEventV2();
    // populate all fields including costCenter
    return event;
}
```

### Step 3 — Register both handlers in consumers

```java
VersionedEventDeserializer deserializer = new VersionedEventDeserializer();

// Keep v1 handler for messages in-flight during deployment
deserializer.register(TOPIC_HR_EMPLOYEE_CHANGED, 1, json -> {
    EmployeeChangedEvent event = deserializer.parse(json, EmployeeChangedEvent.class);
    handleV1(event);
    return null;
});

// New v2 handler
deserializer.register(TOPIC_HR_EMPLOYEE_CHANGED, 2, json -> {
    EmployeeChangedEventV2 event = deserializer.parse(json, EmployeeChangedEventV2.class);
    handleV2(event);
    return null;
});
```

### Step 4 — Deploy and monitor

Deploy consumers first (they now handle both v1 and v2), then deploy the updated producer. Monitor the DLQ for any deserialization failures.

### Step 5 — Clean up

After all consumers have migrated and no v1 messages remain in the topic (check consumer lag), remove the v1 handler registration.

---

## Dead-Letter Queue Handling

If a consumer receives a message with an unknown version (no registered handler), a `VersionedEventDeserializer.UnknownEventVersionException` is thrown. The Kafka error handler will route the message to the DLQ topic (`{topic}.dlq`) after 3 retry attempts. Operations should monitor DLQ topics for version mismatch errors during schema migrations.
