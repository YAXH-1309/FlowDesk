package com.flowdesk.core.kafka.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published to {@code audit.events} by all modules when a business entity is created, updated, or deleted.
 * Schema version: 1
 */
public class AuditEvent extends KafkaEvent {

    @JsonProperty("auditId")
    private UUID auditId;

    @JsonProperty("tenantId")
    private UUID tenantId;

    @JsonProperty("actorId")
    private UUID actorId;

    @JsonProperty("action")
    private String action;

    @JsonProperty("entityType")
    private String entityType;

    @JsonProperty("entityId")
    private UUID entityId;

    @JsonProperty("timestamp")
    private OffsetDateTime timestamp;

    @JsonProperty("beforeSnapshot")
    private String beforeSnapshot;

    @JsonProperty("afterSnapshot")
    private String afterSnapshot;

    public AuditEvent() {}

    public UUID getAuditId() { return auditId; }
    public void setAuditId(UUID auditId) { this.auditId = auditId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getActorId() { return actorId; }
    public void setActorId(UUID actorId) { this.actorId = actorId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }

    public String getBeforeSnapshot() { return beforeSnapshot; }
    public void setBeforeSnapshot(String beforeSnapshot) { this.beforeSnapshot = beforeSnapshot; }

    public String getAfterSnapshot() { return afterSnapshot; }
    public void setAfterSnapshot(String afterSnapshot) { this.afterSnapshot = afterSnapshot; }
}
