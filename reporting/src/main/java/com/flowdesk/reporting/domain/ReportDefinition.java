package com.flowdesk.reporting.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "report_definitions", schema = "reporting_schema")
public class ReportDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "source_module", nullable = false, length = 50)
    private String sourceModule;

    @Column(name = "filter_criteria", columnDefinition = "jsonb")
    private String filterCriteria;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "grouping_fields", columnDefinition = "text[]")
    private String[] groupingFields;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "output_columns", columnDefinition = "text[]")
    private String[] outputColumns;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() { if (createdAt == null) createdAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }
    public String getFilterCriteria() { return filterCriteria; }
    public void setFilterCriteria(String filterCriteria) { this.filterCriteria = filterCriteria; }
    public String[] getGroupingFields() { return groupingFields; }
    public void setGroupingFields(String[] groupingFields) { this.groupingFields = groupingFields; }
    public String[] getOutputColumns() { return outputColumns; }
    public void setOutputColumns(String[] outputColumns) { this.outputColumns = outputColumns; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
