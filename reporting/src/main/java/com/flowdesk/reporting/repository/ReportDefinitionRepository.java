package com.flowdesk.reporting.repository;

import com.flowdesk.reporting.domain.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {
    Optional<ReportDefinition> findByIdAndTenantId(UUID id, UUID tenantId);
}
