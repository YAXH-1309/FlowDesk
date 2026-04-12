package com.flowdesk.inventory.repository;

import com.flowdesk.inventory.domain.Sku;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SkuRepository extends JpaRepository<Sku, UUID> {
    Optional<Sku> findByIdAndTenantId(UUID id, UUID tenantId);
}
