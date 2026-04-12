package com.flowdesk.accounting.repository;

import com.flowdesk.accounting.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Account> findByTenantId(UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id AND a.tenantId = :tenantId")
    Optional<Account> findByIdAndTenantIdForUpdate(UUID id, UUID tenantId);
}
