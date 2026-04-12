package com.flowdesk.task.repository;

import com.flowdesk.task.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByTenantIdAndDeletedAtIsNull(UUID tenantId);
    Optional<Project> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
