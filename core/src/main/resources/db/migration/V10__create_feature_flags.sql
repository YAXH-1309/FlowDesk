-- Feature flags table for task 25
CREATE TABLE IF NOT EXISTS core_schema.feature_flags (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID,
    user_id             UUID,
    flag_key            VARCHAR(255) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    rollout_percentage  INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_feature_flags_key ON core_schema.feature_flags(flag_key);
CREATE INDEX idx_feature_flags_tenant ON core_schema.feature_flags(tenant_id) WHERE tenant_id IS NOT NULL;
