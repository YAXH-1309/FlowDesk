package com.flowdesk.core.featureflag;

import com.flowdesk.core.context.TenantContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Evaluates feature flags: global default → tenant override → user override → percentage rollout.
 * Results are cached in L1 (Caffeine) with a 30-second TTL via Spring Cache.
 */
@Service
public class FeatureFlagService {

    private final FeatureFlagRepository repository;

    public FeatureFlagService(FeatureFlagRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "featureFlags", key = "#flagKey + ':' + #tenantId + ':' + #userId")
    public boolean isEnabled(String flagKey, UUID tenantId, UUID userId) {
        List<FeatureFlagEntry> entries = repository.findByFlagKey(flagKey);
        if (entries.isEmpty()) return false;

        // User-level override
        if (userId != null) {
            for (FeatureFlagEntry e : entries) {
                if (userId.equals(e.getUserId()) && e.getTenantId() == null) {
                    return e.isEnabled();
                }
            }
        }

        // Tenant-level override
        if (tenantId != null) {
            for (FeatureFlagEntry e : entries) {
                if (tenantId.equals(e.getTenantId()) && e.getUserId() == null) {
                    return e.isEnabled();
                }
            }
        }

        // Global default (no tenant, no user)
        for (FeatureFlagEntry e : entries) {
            if (e.getTenantId() == null && e.getUserId() == null) {
                if (e.getRolloutPercentage() > 0 && e.getRolloutPercentage() < 100) {
                    // Hash-based deterministic rollout
                    String hashInput = flagKey + ":" + (tenantId != null ? tenantId : "");
                    int bucket = Math.abs(hashInput.hashCode() % 100);
                    return bucket < e.getRolloutPercentage();
                }
                return e.isEnabled();
            }
        }
        return false;
    }

    @Transactional
    @CacheEvict(value = "featureFlags", allEntries = true)
    public FeatureFlagEntry upsert(String flagKey, boolean enabled, UUID tenantId, UUID userId, int rolloutPct) {
        List<FeatureFlagEntry> existing = repository.findByFlagKey(flagKey);
        FeatureFlagEntry entry = existing.stream()
                .filter(e -> java.util.Objects.equals(e.getTenantId(), tenantId)
                        && java.util.Objects.equals(e.getUserId(), userId))
                .findFirst()
                .orElseGet(FeatureFlagEntry::new);
        entry.setFlagKey(flagKey);
        entry.setEnabled(enabled);
        entry.setTenantId(tenantId);
        entry.setUserId(userId);
        entry.setRolloutPercentage(rolloutPct);
        return repository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<FeatureFlagEntry> getAll() {
        return repository.findAll();
    }
}
