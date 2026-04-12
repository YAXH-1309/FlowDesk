package com.flowdesk.accounting.repository;

import com.flowdesk.accounting.domain.ApInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ApInvoiceRepository extends JpaRepository<ApInvoice, UUID> {
    @Query("SELECT i FROM ApInvoice i WHERE i.dueDate < :today AND i.status NOT IN ('PAID','DISPUTED')")
    List<ApInvoice> findOverdue(LocalDate today);
}
