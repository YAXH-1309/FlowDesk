package com.flowdesk.accounting.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AccountingOutboxRepository extends JpaRepository<AccountingOutboxEvent, UUID> {
    @Query("SELECT e FROM AccountingOutboxEvent e WHERE e.publishedAt IS NULL ORDER BY e.createdAt")
    List<AccountingOutboxEvent> findUnpublished();
}
