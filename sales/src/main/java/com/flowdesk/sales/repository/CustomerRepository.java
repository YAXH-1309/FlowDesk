package com.flowdesk.sales.repository;

import com.flowdesk.sales.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);
}
