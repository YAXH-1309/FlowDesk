CREATE SCHEMA IF NOT EXISTS reporting_schema;

CREATE TABLE reporting_schema.report_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    source_module   VARCHAR(50) NOT NULL,
    filter_criteria JSONB,
    grouping_fields TEXT[],
    output_columns  TEXT[],
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reporting_schema.report_exports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    report_id       UUID NOT NULL REFERENCES reporting_schema.report_definitions(id),
    format          VARCHAR(10) NOT NULL CHECK (format IN ('CSV','XLSX')),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSING','READY','FAILED')),
    s3_key          VARCHAR(500),
    requested_by    UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ
);

CREATE TABLE reporting_schema.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0
);
