package com.flowdesk.sales.repository;

import com.flowdesk.sales.domain.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {
    Optional<SalesOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM SalesOrder o " +
           "WHERE o.customerId = :customerId AND o.tenantId = :tenantId " +
           "AND o.status NOT IN ('CANCELLED','INVOICED')")
    BigDecimal sumOutstandingByCustomer(UUID customerId, UUID tenantId);
}
