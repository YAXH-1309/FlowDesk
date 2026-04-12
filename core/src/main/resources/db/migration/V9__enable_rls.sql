-- Task 9.3: Enable PostgreSQL Row-Level Security on all tenant-scoped tables
-- and create isolation policies using app.tenant_id session variable.

-- ── task_schema ───────────────────────────────────────────────────────────────
ALTER TABLE task_schema.projects ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON task_schema.projects
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE task_schema.tasks ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON task_schema.tasks
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- ── hr_schema ─────────────────────────────────────────────────────────────────
ALTER TABLE hr_schema.employees ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_schema.employees
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE hr_schema.performance_reviews ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hr_schema.performance_reviews
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- ── inventory_schema ──────────────────────────────────────────────────────────
ALTER TABLE inventory_schema.skus ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON inventory_schema.skus
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE inventory_schema.stock ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON inventory_schema.stock
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE inventory_schema.purchase_orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON inventory_schema.purchase_orders
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- ── accounting_schema ─────────────────────────────────────────────────────────
ALTER TABLE accounting_schema.accounts ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON accounting_schema.accounts
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- ── sales_schema ──────────────────────────────────────────────────────────────
ALTER TABLE sales_schema.customers ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sales_schema.customers
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE sales_schema.opportunities ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sales_schema.opportunities
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE sales_schema.orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sales_schema.orders
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
