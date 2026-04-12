package com.flowdesk.hr.repository;

import com.flowdesk.hr.domain.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {
}
