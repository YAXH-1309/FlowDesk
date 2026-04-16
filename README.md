# Flowdesk Pro

A multi-tenant SaaS platform combining project management with enterprise ERP modules — HR, Inventory, Accounting, Sales, and Reporting. Built as a modular monolith in Spring Boot 3.x with a React 18 frontend.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.2, Gradle multi-module |
| Frontend | React 18.3, TypeScript, Vite, React Query, Zustand |
| Database | PostgreSQL 16 (primary + 3 read replicas) |
| Cache | Caffeine L1 + Redis Cluster L2 |
| Messaging | Apache Kafka 3.6 (3 brokers, KRaft) |
| Search | Elasticsearch 8.13 |
| Observability | Prometheus, Grafana, Jaeger, ELK |
| Infrastructure | Docker Compose (local), Kubernetes on AWS EKS (production) |

## Prerequisites

- Docker Desktop
- Java 21 (for local Gradle builds without Docker)
- Node.js 20+ (for frontend development without Docker)

## Getting Started

Clone the repo and start the full stack:

```bash
git clone <repo-url>
cd flowdesk
docker compose up --build
```

The first build compiles the entire Gradle multi-module project and may take a few minutes.

### Services

| Service | URL |
|---|---|
| Backend API | http://localhost:8080 |
| Frontend | http://localhost:3000 |
| Swagger UI | http://localhost:8080/api/v1/swagger-ui.html |
| Grafana | http://localhost:3001 |
| Jaeger | http://localhost:16686 |
| Prometheus | http://localhost:9090 |

Grafana default credentials: `admin` / `admin`

## Project Structure

```
flowdesk/
├── core/           # Shared library — BaseEntity, TenantContext, caching, audit log, outbox
├── auth/           # Identity, JWT, OAuth2/SAML, RBAC
├── task/           # Project and task management
├── hr/             # Employees, attendance, payroll, performance reviews
├── inventory/      # SKUs, stock, purchase orders
├── accounting/     # Double-entry ledger, AP/AR invoices, budgets, financial reports
├── sales/          # CRM pipeline, orders, credit hold, customer interactions
├── reporting/      # Dashboards, custom reports, async exports, full-text search
├── frontend/       # React SPA
├── infra/          # Kubernetes manifests, Terraform IaC, Prometheus config, Grafana provisioning
├── docker-compose.yml
└── Dockerfile.backend
```

Each module owns its own PostgreSQL schema and Flyway migrations.

## Modules

- **Auth** — JWT authentication, refresh token rotation, OAuth2 (Google), SAML 2.0 (Okta), 7-role RBAC
- **Task** — Projects and tasks with soft-delete, status tracking, and cross-tenant assignment validation
- **HR** — Employee records, attendance, payroll runs with distributed locking, performance reviews
- **Inventory** — SKU/stock management, low-stock event publishing, purchase orders
- **Accounting** — Double-entry journal entries, trial balance, income statement, balance sheet, AP/AR invoicing
- **Sales** — Opportunity pipeline, auto order creation on CLOSED_WON, credit hold logic, interaction tracking
- **Reporting** — CQRS dashboards, custom reports, async CSV/XLSX exports to S3, Elasticsearch full-text search

## Key Features

**Multi-tenancy** — Tenant ID from JWT injected into all queries; PostgreSQL Row-Level Security enforced on all tenant-scoped tables.

**Caching** — Two-level: Caffeine (L1) → Redis (L2) → DB. Invalidated on writes. Graceful fallback to L1 if Redis is unavailable.

**Idempotency** — `Idempotency-Key` header required on critical POST endpoints. Responses cached in Redis for 24 hours.

**Distributed locking** — Redisson-based locks on payroll runs, order confirmation, and stock updates. 30-second TTL safety net.

**Transactional outbox** — DB write and Kafka publish are atomic. Background relay guarantees at-least-once delivery.

**Audit log** — Immutable append-only log. `UPDATE`/`DELETE` revoked at the database level for all application roles.

**Rate limiting** — Sliding window via Redis: 100 req/min per user, 20 req/min per unauthenticated IP. Returns 429 + `Retry-After`.

## API

All endpoints follow the `/api/v1/` prefix. Full OpenAPI spec available at:

```
http://localhost:8080/api/v1/swagger-ui.html
```

Authentication uses Bearer JWT in the `Authorization` header. Obtain a token via `POST /api/v1/auth/login`.

## Running Tests

```bash
./gradlew test
```

The test suite includes 20 property-based tests using [jqwik](https://jqwik.net/) covering correctness invariants across all modules (double-entry ledger, tenant isolation, RBAC, cache ordering, outbox atomicity, and more). Each property runs 100+ randomized iterations.

## Environment Variables

Key variables (defaults work for local Docker Compose):

| Variable | Default | Description |
|---|---|---|
| `DB_PRIMARY_URL` | `jdbc:postgresql://postgres-primary:5432/flowdesk` | Primary DB URL |
| `DB_USERNAME` | `flowdesk` | DB username |
| `DB_PASSWORD` | `flowdesk` | DB password |
| `APP_JWT_SECRET` | (base64 key in `.env`) | JWT signing secret |
| `SPRING_DATA_REDIS_HOST` | `redis` | Redis host |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka1:9092,...` | Kafka brokers |
| `SPRING_ELASTICSEARCH_URIS` | `http://elasticsearch:9200` | Elasticsearch URI |
| `FRONTEND_URL` | `http://localhost:3000` | CORS allowed origin |

Override via `.env` file at the project root (already provided for local dev).

## Production Deployment

Infrastructure is defined as Terraform IaC in `infra/terraform/`. Targets AWS:

- EKS (Kubernetes 1.29, HPA 3–20 pods)
- RDS PostgreSQL Multi-AZ
- ElastiCache Redis Cluster
- Amazon MSK (Kafka)
- S3 with cross-region replication
- Route 53 multi-region failover (RTO ≤ 15 min)

CI/CD via GitHub Actions: PR validation → build + push to ECR → blue-green deploy to EKS with auto-rollback.

## Performance Targets

| Metric | Target |
|---|---|
| P95 latency | < 100ms |
| Concurrent users | 10,000+ |
| Availability | 99.97% |
| RTO (failover) | ≤ 15 minutes |

