package com.flowdesk.hr.repository;

import com.flowdesk.hr.domain.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {
}
