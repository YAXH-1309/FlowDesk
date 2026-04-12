package com.flowdesk.sales.outbox;

import com.flowdesk.core.outbox.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_events", schema = "sales_schema")
public class SalesOutboxEvent extends OutboxEvent {
}
