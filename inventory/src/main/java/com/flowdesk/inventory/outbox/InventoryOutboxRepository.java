package com.flowdesk.inventory.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface InventoryOutboxRepository extends JpaRepository<InventoryOutboxEvent, UUID> {
    @Query("SELECT e FROM InventoryOutboxEvent e WHERE e.publishedAt IS NULL ORDER BY e.createdAt")
    List<InventoryOutboxEvent> findUnpublished();
}
