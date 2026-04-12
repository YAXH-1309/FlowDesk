package com.flowdesk.accounting.repository;

import com.flowdesk.accounting.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
}
