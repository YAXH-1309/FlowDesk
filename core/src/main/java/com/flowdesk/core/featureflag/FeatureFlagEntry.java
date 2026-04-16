package com.flowdesk.core.featureflag;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "feature_flags", schema = "core_schema")
public class FeatureFlagEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "flag_key", nullable = false)
    private String flagKey;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "rollout_percentage")
    private int rolloutPercentage;

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getFlagKey() { return flagKey; }
    public void setFlagKey(String flagKey) { this.flagKey = flagKey; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRolloutPercentage() { return rolloutPercentage; }
    public void setRolloutPercentage(int rolloutPercentage) { this.rolloutPercentage = rolloutPercentage; }
}
