package com.flowdesk.task.repository;

import com.flowdesk.task.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByProjectIdAndTenantIdAndDeletedAtIsNull(UUID projectId, UUID tenantId);
    Optional<Task> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
