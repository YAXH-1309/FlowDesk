package com.flowdesk.reporting.repository;

import com.flowdesk.reporting.domain.ReportExport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportExportRepository extends JpaRepository<ReportExport, UUID> {
}
