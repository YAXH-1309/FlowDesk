package com.flowdesk.sales.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SalesOutboxRepository extends JpaRepository<SalesOutboxEvent, UUID> {
    @Query("SELECT e FROM SalesOutboxEvent e WHERE e.publishedAt IS NULL ORDER BY e.createdAt")
    List<SalesOutboxEvent> findUnpublished();
}
