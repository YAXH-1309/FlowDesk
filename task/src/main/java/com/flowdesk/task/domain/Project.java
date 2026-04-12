package com.flowdesk.task.domain;

import com.flowdesk.core.domain.BaseEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "projects", schema = "task_schema")
public class Project extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner_id", nullable = false)
    private java.util.UUID ownerId;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public java.util.UUID getOwnerId() { return ownerId; }
    public void setOwnerId(java.util.UUID ownerId) { this.ownerId = ownerId; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
