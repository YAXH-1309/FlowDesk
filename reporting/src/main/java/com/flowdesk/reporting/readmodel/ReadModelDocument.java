package com.flowdesk.reporting.readmodel;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.Map;

/**
 * Unified Elasticsearch read model document for CQRS.
 * All module entities are projected into this document for reporting queries.
 * The write model (PostgreSQL) is never queried by GET /api/v1/reporting/... endpoints.
 */
@Document(indexName = "flowdesk_read_model")
public class ReadModelDocument {

    @Id
    private String id; // "{module}_{entityType}_{entityId}"

    @Field(type = FieldType.Keyword)
    private String module; // hr, sales, inventory, accounting, task

    @Field(type = FieldType.Keyword)
    private String entityType; // employee, order, sku, invoice, task, etc.

    @Field(type = FieldType.Keyword)
    private String entityId;

    @Field(type = FieldType.Keyword)
    private String tenantId;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String displayName;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Object, enabled = false)
    private Map<String, Object> attributes; // module-specific fields stored as-is

    @Field(type = FieldType.Date)
    private Instant eventTime;

    @Field(type = FieldType.Date)
    private Instant indexedAt;

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }

    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }

    public Instant getIndexedAt() { return indexedAt; }
    public void setIndexedAt(Instant indexedAt) { this.indexedAt = indexedAt; }
}
