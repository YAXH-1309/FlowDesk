package com.flowdesk.accounting.outbox;

import com.flowdesk.core.outbox.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_events", schema = "accounting_schema")
public class AccountingOutboxEvent extends OutboxEvent {
}
