package com.flowdesk.accounting.repository;

import com.flowdesk.accounting.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
}
