package com.flowdesk.core.featureflag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlagEntry, UUID> {
    List<FeatureFlagEntry> findByFlagKey(String flagKey);
    List<FeatureFlagEntry> findByFlagKeyAndTenantId(String flagKey, UUID tenantId);
    List<FeatureFlagEntry> findByFlagKeyAndUserId(String flagKey, UUID userId);
}
