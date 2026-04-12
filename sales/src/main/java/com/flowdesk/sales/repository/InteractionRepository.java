package com.flowdesk.sales.repository;

import com.flowdesk.sales.domain.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {
}
