CREATE SCHEMA IF NOT EXISTS core_schema;

CREATE TABLE core_schema.tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE core_schema.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES core_schema.tenants(id),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    roles           TEXT[] NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Audit log: append-only, partitioned by month
CREATE TABLE core_schema.audit_log (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    actor_id        UUID NOT NULL,
    action          VARCHAR(50) NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       UUID NOT NULL,
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT now(),
    before_snapshot JSONB,
    after_snapshot  JSONB
) PARTITION BY RANGE (timestamp);

-- Monthly partitions: 2024-01 through 2025-12
CREATE TABLE core_schema.audit_log_2024_01 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE core_schema.audit_log_2024_02 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
CREATE TABLE core_schema.audit_log_2024_03 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');
CREATE TABLE core_schema.audit_log_2024_04 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-04-01') TO ('2024-05-01');
CREATE TABLE core_schema.audit_log_2024_05 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-05-01') TO ('2024-06-01');
CREATE TABLE core_schema.audit_log_2024_06 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
CREATE TABLE core_schema.audit_log_2024_07 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-07-01') TO ('2024-08-01');
CREATE TABLE core_schema.audit_log_2024_08 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-08-01') TO ('2024-09-01');
CREATE TABLE core_schema.audit_log_2024_09 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-09-01') TO ('2024-10-01');
CREATE TABLE core_schema.audit_log_2024_10 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-10-01') TO ('2024-11-01');
CREATE TABLE core_schema.audit_log_2024_11 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');
CREATE TABLE core_schema.audit_log_2024_12 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');
CREATE TABLE core_schema.audit_log_2025_01 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE core_schema.audit_log_2025_02 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
CREATE TABLE core_schema.audit_log_2025_03 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');
CREATE TABLE core_schema.audit_log_2025_04 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');
CREATE TABLE core_schema.audit_log_2025_05 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
CREATE TABLE core_schema.audit_log_2025_06 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');
CREATE TABLE core_schema.audit_log_2025_07 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');
CREATE TABLE core_schema.audit_log_2025_08 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');
CREATE TABLE core_schema.audit_log_2025_09 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');
CREATE TABLE core_schema.audit_log_2025_10 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
CREATE TABLE core_schema.audit_log_2025_11 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
CREATE TABLE core_schema.audit_log_2025_12 PARTITION OF core_schema.audit_log
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

-- Revoke UPDATE/DELETE on audit_log for PUBLIC (all application roles)
REVOKE UPDATE, DELETE ON core_schema.audit_log FROM PUBLIC;
