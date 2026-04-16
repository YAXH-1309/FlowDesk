CREATE SCHEMA IF NOT EXISTS hr_schema;

CREATE TABLE hr_schema.employees (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    full_name           VARCHAR(255) NOT NULL,
    department          VARCHAR(100),
    job_title           VARCHAR(100),
    employment_status   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    start_date          DATE NOT NULL,
    base_salary         NUMERIC(15,2),
    currency            CHAR(3),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE hr_schema.attendance (
    id              UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    employee_id     UUID NOT NULL,
    date            DATE NOT NULL,
    check_in        TIMESTAMPTZ,
    check_out       TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT','ABSENT','LATE','ON_LEAVE'))
) PARTITION BY RANGE (date);

-- Monthly partitions: 2024-01 through 2025-12
CREATE TABLE hr_schema.attendance_2024_01 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE hr_schema.attendance_2024_02 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
CREATE TABLE hr_schema.attendance_2024_03 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');
CREATE TABLE hr_schema.attendance_2024_04 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-04-01') TO ('2024-05-01');
CREATE TABLE hr_schema.attendance_2024_05 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-05-01') TO ('2024-06-01');
CREATE TABLE hr_schema.attendance_2024_06 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
CREATE TABLE hr_schema.attendance_2024_07 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-07-01') TO ('2024-08-01');
CREATE TABLE hr_schema.attendance_2024_08 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-08-01') TO ('2024-09-01');
CREATE TABLE hr_schema.attendance_2024_09 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-09-01') TO ('2024-10-01');
CREATE TABLE hr_schema.attendance_2024_10 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-10-01') TO ('2024-11-01');
CREATE TABLE hr_schema.attendance_2024_11 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');
CREATE TABLE hr_schema.attendance_2024_12 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');
CREATE TABLE hr_schema.attendance_2025_01 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE hr_schema.attendance_2025_02 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
CREATE TABLE hr_schema.attendance_2025_03 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');
CREATE TABLE hr_schema.attendance_2025_04 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');
CREATE TABLE hr_schema.attendance_2025_05 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
CREATE TABLE hr_schema.attendance_2025_06 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');
CREATE TABLE hr_schema.attendance_2025_07 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');
CREATE TABLE hr_schema.attendance_2025_08 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');
CREATE TABLE hr_schema.attendance_2025_09 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');
CREATE TABLE hr_schema.attendance_2025_10 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
CREATE TABLE hr_schema.attendance_2025_11 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
CREATE TABLE hr_schema.attendance_2025_12 PARTITION OF hr_schema.attendance
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

CREATE TABLE hr_schema.performance_reviews (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    employee_id     UUID NOT NULL,
    reviewer_id     UUID NOT NULL,
    review_period   VARCHAR(20) NOT NULL,
    rating          NUMERIC(3,1) NOT NULL,
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE hr_schema.payroll_runs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    pay_period_start    DATE NOT NULL,
    pay_period_end      DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE hr_schema.payroll_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          UUID NOT NULL REFERENCES hr_schema.payroll_runs(id),
    employee_id     UUID NOT NULL,
    gross_pay       NUMERIC(15,2) NOT NULL,
    deductions      NUMERIC(15,2) NOT NULL,
    net_pay         NUMERIC(15,2) NOT NULL
);

CREATE TABLE hr_schema.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0
);
