# Flowdesk Pro — Product Report

## Overview

Flowdesk Pro is a multi-tenant SaaS platform built as a modular monolith in Java (Spring Boot 3.x) with a React 18 frontend. It combines project and task management with five enterprise ERP modules: HR, Inventory, Accounting, Sales, and Reporting. The platform is designed to serve 10,000+ concurrent users with P95 latency under 100ms and 99.97% availability.

---

## Architecture

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.2, Java 21, Gradle multi-module |
| Frontend | React 18.3, TypeScript, Vite, React Query, Zustand |
| Database | PostgreSQL 16 — primary + 3 read replicas |
| Cache | Caffeine (L1, in-process) + Redis Cluster (L2) |
| Messaging | Apache Kafka 3.6 — 3 brokers, KRaft mode |
| Search | Elasticsearch 8.13 |
| Observability | Prometheus, Grafana, ELK, Jaeger |
| Container | Docker Compose (local), Kubernetes on AWS EKS (production) |

The backend is a single deployable JAR with 8 independent modules (`core`, `auth`, `task`, `hr`, `inventory`, `accounting`, `sales`, `reporting`), each owning its own PostgreSQL schema and Flyway migrations.

---

## Modules

### Auth
Handles identity, session management, and access control.

Endpoints:
- `POST /api/v1/auth/register` — register with email/password, returns JWT
- `POST /api/v1/auth/login` — returns JWT + HttpOnly refresh token cookie
- `POST /api/v1/auth/refresh` — token rotation without re-authentication
- `POST /api/v1/auth/logout` — revokes refresh token
- `GET /api/v1/auth/oauth2/callback` — OAuth2 provider callback
- `POST /api/v1/auth/saml/acs` — SAML 2.0 assertion consumer

Key features:
- JWT with 24-hour expiry; refresh tokens valid for 30 days
- bcrypt password hashing at cost factor 12
- OAuth2 (Google) and SAML 2.0 (Okta) with external identity mapping
- RBAC with 7 roles: `VIEWER`, `MEMBER`, `ADMIN`, `HR_ADMIN`, `MANAGER`, `FINANCE`, `SALES_REP`
- AOP-based role enforcement via `@RequiresRole` on all protected endpoints

---

### Task
Trello-style project and task management.

Endpoints:
- `POST /api/v1/tasks/projects` — create project
- `GET /api/v1/tasks/projects` — list projects (tenant-scoped)
- `POST /api/v1/tasks/projects/{id}/tasks` — create task
- `PUT /api/v1/tasks/tasks/{id}` — update task
- `DELETE /api/v1/tasks/tasks/{id}` — soft-delete task
- `PUT /api/v1/tasks/tasks/{id}/assign` — assign task (cross-tenant validation)

Domain models: `Project`, `Task` (statuses: TODO / IN_PROGRESS / REVIEW / DONE)

---

### HR
Employee lifecycle, attendance, payroll, and performance reviews.

Endpoints:
- `POST /api/v1/hr/employees` — create employee record
- `PUT /api/v1/hr/employees/{id}` — update employee
- `POST /api/v1/hr/attendance` — record attendance
- `POST /api/v1/hr/payroll/run` — initiate payroll run (idempotency key required)
- `GET /api/v1/hr/payroll/{runId}/report` — payroll report
- `POST /api/v1/hr/reviews` — submit performance review

Key features:
- Payroll calculation: gross pay minus statutory deductions (20% flat), net pay
- Distributed lock prevents concurrent payroll runs for the same tenant/period
- Employee change and review events published to Kafka via transactional outbox
- Attendance table partitioned by month

---

### Inventory
SKU management, stock tracking, and purchase orders.

Endpoints:
- `POST /api/v1/inventory/skus` — create SKU
- `PUT /api/v1/inventory/skus/{id}/stock` — adjust stock (idempotency key required)
- `POST /api/v1/inventory/purchase-orders` — create purchase order
- `PUT /api/v1/inventory/purchase-orders/{id}/receive` — receive PO and update stock

Key features:
- Low-stock event published to Kafka when `quantity_on_hand <= reorder_threshold`
- Distributed lock on stock updates prevents lost updates under concurrency
- PO receipt and stock adjustment are atomic in a single transaction
- Warehouse-level stock tracking per SKU

---

### Accounting
Double-entry bookkeeping, financial reports, AP/AR invoicing, and budgets.

Endpoints:
- `POST /api/v1/accounting/journal-entries` — post journal entry
- `GET /api/v1/accounting/accounts/{id}/balance` — account balance
- `GET /api/v1/accounting/reports/trial-balance` — trial balance
- `GET /api/v1/accounting/reports/income-statement` — income statement
- `GET /api/v1/accounting/reports/balance-sheet` — balance sheet
- `POST /api/v1/accounting/ap/invoices` — create AP invoice (idempotency key required)
- `POST /api/v1/accounting/ar/invoices` — create AR invoice (idempotency key required)
- `POST /api/v1/accounting/budgets` — create budget

Key features:
- Double-entry invariant enforced: `SUM(journal_lines.amount) = 0` per entry; imbalanced entries rejected
- Account balances updated atomically via `SELECT FOR UPDATE`
- Overdue AR invoice detection via scheduled task; publishes `accounting.invoice.overdue` event
- Journal entries partitioned by month
- Account types: ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE

---

### Sales
CRM pipeline, order management, and customer interactions.

Endpoints:
- `POST /api/v1/sales/customers` — create customer with credit limit and payment terms
- `POST /api/v1/sales/opportunities` — create opportunity
- `PUT /api/v1/sales/opportunities/{id}` — update opportunity stage
- `POST /api/v1/sales/orders` — create sales order (idempotency key required)
- `PUT /api/v1/sales/orders/{id}/confirm` — confirm order
- `POST /api/v1/sales/orders/{id}/invoice` — generate invoice
- `POST /api/v1/sales/interactions` — record customer interaction (call, email, meeting)

Key features:
- Opportunity stages: PROSPECT → QUALIFIED → PROPOSAL → NEGOTIATION → CLOSED_WON / CLOSED_LOST
- CLOSED_WON automatically creates a linked sales order within 5 seconds (async event)
- Credit hold: if `outstanding_balance + order_value > credit_limit`, order is held and `sales.credit-hold` event is published
- Order confirmation publishes `sales.order.confirmed` via outbox within 500ms
- Distributed lock on order confirmation prevents double-confirm

---

### Reporting
Analytics dashboards, custom reports, async exports, and full-text search.

Endpoints:
- `GET /api/v1/reporting/dashboards/{module}` — pre-built dashboard metrics (5-min Redis cache)
- `POST /api/v1/reporting/reports` — define custom report
- `POST /api/v1/reporting/reports/{id}/execute` — execute report (sync, max 10,000 rows)
- `GET /api/v1/reporting/reports/{id}/export` — async CSV/XLSX export
- `GET /api/v1/reporting/search?q=...` — full-text search via Elasticsearch

Key features:
- CQRS: write model in PostgreSQL, read model in Elasticsearch (synced via Kafka consumers)
- Cursor-based (keyset) pagination — default page size 1,000 rows, prevents full-table scans
- Sync execution capped at 10,000 rows; larger datasets auto-routed to async export (HTTP 422 otherwise)
- Async exports stored in S3; user notified via event when ready
- Full-text search SLA: 500ms for indexed datasets
- RBAC filtering: results scoped to resources the requesting user is authorized to view

---

## Cross-Cutting Concerns

### Multi-Tenancy
- Tenant ID extracted from JWT and stored in `TenantContext` (ThreadLocal)
- All repository queries filtered by `tenant_id`
- PostgreSQL Row-Level Security (RLS) enforced on all tenant-scoped tables
- `app.tenant_id` session variable set on every DB connection
- Cross-tenant access returns 403 without revealing resource existence

### Caching
- L1: Caffeine (in-process, sub-millisecond)
- L2: Redis Cluster (shared across instances)
- Lookup chain: L1 → L2 → DB
- Cache invalidated on entity writes within 1 second
- Redis unavailable → graceful fallback to L1 only
- Cache hit/miss metrics exported to Prometheus

### Idempotency
- `Idempotency-Key` header (UUID) required on critical POST endpoints
- First execution stores `{ requestHash, responseStatus, responseBody }` in Redis (24-hour TTL)
- Subsequent requests with the same key return the cached response immediately
- Key reused with a different request body returns HTTP 422

### Distributed Locking
- Redisson-based `DistributedLockService` wrapping `RLock`
- Applied to: payroll runs (`lock:payroll:{tenantId}:{payPeriod}`), order confirmation (`lock:order:{orderId}`), stock updates (`lock:stock:{skuId}:{warehouseId}`)
- 30-second TTL as safety net against crash-without-release
- Locks always released in `finally` block

### Transactional Outbox
- Each module has its own outbox table in its schema
- DB write and outbox entry commit atomically in the same transaction
- Background relay process publishes unpublished entries to Kafka
- Guarantees at-least-once delivery without distributed transactions

### Audit Log
- Immutable append-only log in `core_schema.audit_log`
- Records: actor, action, entity type, entity ID, timestamp, before/after snapshots
- `UPDATE` and `DELETE` revoked at the database level for all application roles
- Applied automatically via `@AuditLog` AOP aspect on service methods

### Rate Limiting
- Sliding window via Redis: `rate:{userId}` (100 req/min), `rate:ip:{ip}` (20 req/min unauthenticated)
- Returns HTTP 429 with `Retry-After` header on limit exceeded
- Redis key TTL equals window duration (60 seconds)

### API Gateway Layer
- `GatewayRoutingFilter` routes requests to module handlers by path prefix
- JWT validation at gateway level — 401 returned before any module code executes
- `X-Correlation-ID` header injected (generated if absent), stored in MDC, forwarded downstream
- Every request logged at INFO: method, path, tenant ID, correlation ID, response status, latency
- Micrometer meters registered: `http.server.requests` tagged by module, method, status

---

## Security

- TLS 1.3 enforced at ALB/Nginx; HTTP → HTTPS redirect returns 301
- JWT 24-hour expiry with refresh token rotation
- bcrypt cost factor 12 for password storage
- RBAC on all endpoints; VIEWER role blocked from all write operations
- Secrets loaded from AWS Secrets Manager — never hardcoded
- SAST and DAST scanning on every CI/CD run
- NetworkPolicies in Kubernetes restrict inter-service communication to explicit allow-lists

---

## Observability

- Structured JSON logs: `timestamp`, `level`, `service`, `traceId`, `message`
- Prometheus metrics: request rate, error rate, latency percentiles, JVM heap, cache hit rates, Kafka consumer lag
- Grafana dashboards: module-specific KPIs, provisioned automatically on startup
- Jaeger distributed tracing: full request trace reconstruction across modules
- Slow query logging for queries exceeding 20ms
- `/actuator/health` with liveness and readiness probes

---

## Event Bus (Kafka Topics)

| Topic | Publisher | Description |
|---|---|---|
| `hr.employee.changed` | HR | Employee record created or updated |
| `hr.review.submitted` | HR | Performance review submitted |
| `inventory.low-stock` | Inventory | Stock quantity crossed reorder threshold |
| `sales.order.confirmed` | Sales | Sales order confirmed |
| `sales.credit-hold` | Sales | Order placed on credit hold |
| `accounting.invoice.overdue` | Accounting | AR invoice past due date |
| `{consumer}.dlq` | Kafka | Dead-letter queue after 3 failed retries |

Consumer retry: exponential backoff (1s → 2s → 4s) then dead-letter routing.

---

## Property-Based Tests (20 Properties)

All properties run via jqwik with 100+ randomized iterations each.

| # | Property | Module |
|---|---|---|
| P1 | Registration produces a valid JWT for any valid credential | Auth |
| P2 | Login is a round-trip of registration | Auth |
| P3 | Refresh token issues a new JWT | Auth |
| P4 | RBAC enforcement is universal across all protected endpoints | Auth |
| P5 | Resource creation is a round-trip | Task |
| P6 | Task updates are reflected in subsequent reads | Task |
| P7 | Tenant isolation — queries never return cross-tenant data | Task |
| P8 | Cross-tenant assignment is rejected | Task |
| P9 | Double-entry ledger invariant holds for all journal entries | Accounting |
| P10 | Rate limiting enforces sliding window with correct HTTP response | Gateway |
| P11 | Cache lookup order is L1 → L2 → database | Core |
| P12 | Cache invalidation is consistent after writes | Core |
| P13 | Dead-letter routing after exhausted retries | Core |
| P14 | Transactional outbox atomicity | Core |
| P15 | Audit log completeness and immutability | Core |
| P16 | Password hashing is irreversible | Auth |
| P17 | Sales credit hold applied for any order exceeding credit limit | Sales |
| P18 | Opportunity closed-won triggers order creation within 5 seconds | Sales |
| P19 | Low-stock events published for any threshold-crossing transaction | Inventory |
| P20 | Payroll calculation correctness | HR |

---

## Infrastructure (AWS)

| Resource | Configuration |
|---|---|
| EKS | Kubernetes 1.29, 3–20 nodes, CPU-based HPA at 70% |
| RDS | PostgreSQL 16, Multi-AZ, db.r6g.xlarge, 30-day backup retention |
| ElastiCache | Redis Cluster, 3 nodes, cache.r6g.large |
| MSK | Kafka 3.6, 3 brokers, kafka.m5.large |
| S3 | Exports + backups with cross-region replication |
| Route 53 | Multi-region failover, RTO ≤ 15 minutes |

Kubernetes pod spec: 500m CPU / 512Mi memory request; 2000m CPU / 2Gi memory limit. 3 replicas per module.

All infrastructure defined as Terraform IaC. Secrets managed via AWS Secrets Manager.

---

## CI/CD Pipeline (GitHub Actions)

**PR validation:** compile → unit tests → integration tests → SAST/DAST scan. Blocks merge on failure or critical vulnerabilities.

**Main branch:** build Docker images → tag with commit SHA → push to ECR → blue-green deploy to EKS → health checks → traffic switch. Auto-rollback on health check failure within a 5-minute window. JMeter performance tests validate P95 < 100ms and error rate < 0.1%.

---

## Local Development

Start the full stack with:

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Backend API | http://localhost:8080 |
| Frontend | http://localhost:3000 |
| Swagger UI | http://localhost:8080/api/v1/swagger-ui.html |
| Grafana | http://localhost:3001 (admin/admin) |
| Jaeger | http://localhost:16686 |
| Prometheus | http://localhost:9090 |

---

## Performance Targets

| Metric | Target |
|---|---|
| P95 latency | < 100ms |
| P99 latency | < 150ms |
| Concurrent users | 10,000+ |
| Availability | 99.97% |
| RTO (failover) | ≤ 15 minutes |
| Search response | < 500ms |
| Report execution (sync) | < 10 seconds for up to 100,000 rows |
| Closed-won order creation | < 5 seconds |
| Order confirmation event | < 500ms |
