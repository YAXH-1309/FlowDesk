package com.flowdesk.hr.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface HrOutboxRepository extends JpaRepository<HrOutboxEvent, UUID> {
    @Query("SELECT e FROM HrOutboxEvent e WHERE e.publishedAt IS NULL ORDER BY e.createdAt")
    List<HrOutboxEvent> findUnpublished();
}
