package com.flowdesk.sales.repository;

import com.flowdesk.sales.domain.Opportunity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {
    Optional<Opportunity> findByIdAndTenantId(UUID id, UUID tenantId);
}
