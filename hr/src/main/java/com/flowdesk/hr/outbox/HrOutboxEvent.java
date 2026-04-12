package com.flowdesk.hr.outbox;

import com.flowdesk.core.outbox.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "outbox_events", schema = "hr_schema")
public class HrOutboxEvent extends OutboxEvent {
}
