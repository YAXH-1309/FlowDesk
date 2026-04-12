package com.flowdesk.hr.repository;

import com.flowdesk.hr.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    List<Employee> findByTenantIdAndEmploymentStatus(UUID tenantId, String status);
    Optional<Employee> findByIdAndTenantId(UUID id, UUID tenantId);
}
