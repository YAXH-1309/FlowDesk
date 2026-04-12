package com.flowdesk.accounting.repository;

import com.flowdesk.accounting.domain.ArInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ArInvoiceRepository extends JpaRepository<ArInvoice, UUID> {
    @Query("SELECT i FROM ArInvoice i WHERE i.dueDate < :today AND i.status NOT IN ('PAID')")
    List<ArInvoice> findOverdue(LocalDate today);
}
