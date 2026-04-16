CREATE SCHEMA IF NOT EXISTS accounting_schema;

CREATE TABLE accounting_schema.accounts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    code        VARCHAR(20) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(20) NOT NULL CHECK (type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    balance     NUMERIC(20,4) NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, code)
);

CREATE TABLE accounting_schema.journal_entries (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    description     VARCHAR(500),
    posted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    posted_by       UUID NOT NULL
) PARTITION BY RANGE (posted_at);

-- Monthly partitions: 2024-01 through 2025-12
CREATE TABLE accounting_schema.journal_entries_2024_01 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE accounting_schema.journal_entries_2024_02 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
CREATE TABLE accounting_schema.journal_entries_2024_03 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');
CREATE TABLE accounting_schema.journal_entries_2024_04 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-04-01') TO ('2024-05-01');
CREATE TABLE accounting_schema.journal_entries_2024_05 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-05-01') TO ('2024-06-01');
CREATE TABLE accounting_schema.journal_entries_2024_06 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
CREATE TABLE accounting_schema.journal_entries_2024_07 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-07-01') TO ('2024-08-01');
CREATE TABLE accounting_schema.journal_entries_2024_08 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-08-01') TO ('2024-09-01');
CREATE TABLE accounting_schema.journal_entries_2024_09 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-09-01') TO ('2024-10-01');
CREATE TABLE accounting_schema.journal_entries_2024_10 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-10-01') TO ('2024-11-01');
CREATE TABLE accounting_schema.journal_entries_2024_11 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');
CREATE TABLE accounting_schema.journal_entries_2024_12 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');
CREATE TABLE accounting_schema.journal_entries_2025_01 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE accounting_schema.journal_entries_2025_02 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
CREATE TABLE accounting_schema.journal_entries_2025_03 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');
CREATE TABLE accounting_schema.journal_entries_2025_04 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');
CREATE TABLE accounting_schema.journal_entries_2025_05 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
CREATE TABLE accounting_schema.journal_entries_2025_06 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');
CREATE TABLE accounting_schema.journal_entries_2025_07 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');
CREATE TABLE accounting_schema.journal_entries_2025_08 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');
CREATE TABLE accounting_schema.journal_entries_2025_09 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');
CREATE TABLE accounting_schema.journal_entries_2025_10 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
CREATE TABLE accounting_schema.journal_entries_2025_11 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
CREATE TABLE accounting_schema.journal_entries_2025_12 PARTITION OF accounting_schema.journal_entries
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

CREATE TABLE accounting_schema.journal_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id        UUID NOT NULL,
    account_id      UUID NOT NULL REFERENCES accounting_schema.accounts(id),
    amount          NUMERIC(20,4) NOT NULL,
    description     VARCHAR(255)
);

CREATE TABLE accounting_schema.ap_invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    supplier_id     UUID NOT NULL,
    amount          NUMERIC(15,2) NOT NULL,
    due_date        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
                    CHECK (status IN ('RECEIVED','APPROVED','SCHEDULED','PAID','DISPUTED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounting_schema.ar_invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    customer_id     UUID NOT NULL,
    amount          NUMERIC(15,2) NOT NULL,
    due_date        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','SENT','PARTIALLY_PAID','PAID','OVERDUE')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounting_schema.budgets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    cost_center     VARCHAR(100) NOT NULL,
    fiscal_period   VARCHAR(20) NOT NULL,
    allocated       NUMERIC(15,2) NOT NULL DEFAULT 0,
    committed       NUMERIC(15,2) NOT NULL DEFAULT 0,
    actual_spend    NUMERIC(15,2) NOT NULL DEFAULT 0
);

CREATE TABLE accounting_schema.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0
);
