package com.flowdesk.inventory.repository;

import com.flowdesk.inventory.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);
}
