package com.flowdesk.inventory.outbox;

import com.flowdesk.core.outbox.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_events", schema = "inventory_schema")
public class InventoryOutboxEvent extends OutboxEvent {
}
