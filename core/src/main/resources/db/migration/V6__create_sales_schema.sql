CREATE SCHEMA IF NOT EXISTS sales_schema;

CREATE TABLE sales_schema.customers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    company_name    VARCHAR(255) NOT NULL,
    contact_email   VARCHAR(255),
    credit_limit    NUMERIC(15,2) NOT NULL DEFAULT 0,
    payment_terms   VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sales_schema.opportunities (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    customer_id UUID NOT NULL REFERENCES sales_schema.customers(id),
    stage       VARCHAR(20) NOT NULL DEFAULT 'PROSPECT'
                CHECK (stage IN ('PROSPECT','QUALIFIED','PROPOSAL','NEGOTIATION','CLOSED_WON','CLOSED_LOST')),
    value       NUMERIC(15,2),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sales_schema.orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    customer_id     UUID NOT NULL REFERENCES sales_schema.customers(id),
    opportunity_id  UUID REFERENCES sales_schema.opportunities(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT','CONFIRMED','FULFILLED','INVOICED','CANCELLED')),
    credit_hold     BOOLEAN NOT NULL DEFAULT FALSE,
    total_amount    NUMERIC(15,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sales_schema.order_lines (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES sales_schema.orders(id),
    description VARCHAR(500) NOT NULL,
    quantity    INT NOT NULL,
    unit_price  NUMERIC(15,4) NOT NULL
);

CREATE TABLE sales_schema.invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    order_id        UUID NOT NULL REFERENCES sales_schema.orders(id),
    invoice_number  VARCHAR(50) NOT NULL UNIQUE,
    subtotal        NUMERIC(15,2) NOT NULL,
    tax             NUMERIC(15,2) NOT NULL DEFAULT 0,
    total           NUMERIC(15,2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sales_schema.interactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    customer_id     UUID REFERENCES sales_schema.customers(id),
    opportunity_id  UUID REFERENCES sales_schema.opportunities(id),
    type            VARCHAR(20) NOT NULL CHECK (type IN ('CALL','EMAIL','MEETING')),
    notes           TEXT,
    author_id       UUID NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sales_schema.outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0
);
