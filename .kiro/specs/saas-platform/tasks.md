# Implementation Plan: Flowdesk SaaS Platform

## Overview

Implement the Flowdesk Pro modular monolith in Java (Spring Boot) with a React frontend. Tasks are ordered to build foundational layers first (core library, auth, data layer) before domain modules, then infrastructure, observability, and frontend. Each task builds on the previous and ends with all components wired together.

## Tasks

- [x] 1. Set up project structure and shared core library
  - [x] 1.1 Initialize Spring Boot multi-module Maven/Gradle project with modules: `core`, `auth`, `task`, `hr`, `inventory`, `accounting`, `sales`, `reporting`
    - Create root `build.gradle` (or `pom.xml`) with dependency management for Spring Boot 3.x, jqwik, Testcontainers, HikariCP, Caffeine, Kafka, jOOQ/JPA
    - Define module boundaries: each module has its own `src/main/java` package under `com.flowdesk.{module}`
    - _Requirements: 4.1, 4.3_
  - [x] 1.2 Implement shared `core` library
    - Create `BaseEntity` with `id` (UUID), `tenantId` (UUID), `createdAt`, `updatedAt`
    - Create `TenantContext` thread-local holder populated from JWT by a servlet filter
    - Create `GlobalExceptionHandler` (`@ControllerAdvice`) mapping all exception types to the standard error envelope JSON
    - Create `ValidationException`, `AuthenticationException`, `AccessDeniedException`, `ResourceNotFoundException`, `ConflictException`, `RateLimitExceededException`, `BusinessRuleException`, `ServiceUnavailableException`
    - Create structured JSON logging configuration (Logback + logstash-logback-encoder) with fields: `timestamp`, `level`, `service`, `traceId`, `message`
    - _Requirements: 4.5, 13.3, 17.1_
  - [x] 1.3 Set up PostgreSQL schemas and Flyway migrations for all modules
    - Create Flyway migration scripts for: `core_schema`, `task_schema`, `hr_schema`, `inventory_schema`, `accounting_schema`, `sales_schema`, `reporting_schema`
    - Include all DDL from the design document (tables, indexes, partitions, constraints)
    - Revoke UPDATE/DELETE on `core_schema.audit_log` for all application roles
    - _Requirements: 20.1, 20.4, 13.4_
  - [x] 1.4 Configure HikariCP connection pool with primary + 3 read-replica routing
    - Define `DataSourceConfig` with a primary `DataSource` and a `ReadReplicaRoutingDataSource` that round-robins across 3 replicas
    - Annotate read-only service methods with `@Transactional(readOnly = true)` to route to replicas
    - Configure pool sizes for 10,000 concurrent users target
    - _Requirements: 20.2, 20.3_
  - [x] 1.5 Write integration test for read/write routing
    - Verify read queries use replica connections and write queries use primary
    - _Requirements: 20.3_
    - Implemented: `core/src/test/java/com/flowdesk/core/config/ReadReplicaRoutingIntegrationTest.java`

- [x] 2. Implement Auth Service (JWT, OAuth2/SAML, RBAC)
  - [x] 2.1 Implement user registration and password hashing
    - Create `POST /api/v1/auth/register` endpoint accepting `{ email, password }`
    - Hash passwords with bcrypt cost factor >= 12 using Spring Security `BCryptPasswordEncoder`
    - Persist user to `core_schema.users`; return 409 on duplicate email
    - Return signed JWT (24-hour expiry) on success
    - _Requirements: 1.1, 1.4, 1.7_
  - [x] 2.2 Write property test P1: Registration produces a valid JWT for any valid credential
    - **Property 1: Registration produces a valid JWT for any valid credential**
    - **Validates: Requirements 1.1, 1.7**
    - Use `@ForAll @Email String email` and `@ForAll @StringLength(min=8) String password` generators
    - Assert JWT is returned with 24-hour expiry; assert stored value is bcrypt hash, not plaintext
    - Implemented: `auth/src/test/java/com/flowdesk/auth/AuthPropertyTest.java#p1_registrationProducesValidJwt`
  - [x] 2.3 Write property test P16: Password hashing is irreversible
    - **Property 16: Password hashing is irreversible**
    - **Validates: Requirements 1.7**
    - For arbitrary password strings, assert stored hash is never equal to plaintext and verifies correctly via bcrypt comparison
    - Implemented: `auth/src/test/java/com/flowdesk/auth/AuthPropertyTest.java#p16_passwordHashingIsIrreversible`
  - [x] 2.4 Implement login, refresh token, and logout
    - Create `POST /api/v1/auth/login` returning JWT + HttpOnly refresh token cookie
    - Create `POST /api/v1/auth/refresh` validating refresh token and issuing new JWT
    - Create `POST /api/v1/auth/logout` invalidating refresh token (204)
    - Return same "Invalid credentials" message for wrong email or wrong password
    - _Requirements: 1.2, 1.3, 1.5, 1.6_
  - [x] 2.5 Write property test P2: Login is a round-trip of registration
    - **Property 2: Login is a round-trip of registration**
    - **Validates: Requirements 1.2, 1.5**
    - For any registered user, assert login returns valid JWT and sets HttpOnly cookie; assert wrong password returns 401 with same message
    - Implemented: `auth/src/test/java/com/flowdesk/auth/AuthPropertyTest.java#p2_loginIsRoundTripOfRegistration`
  - [x] 2.6 Write property test P3: Refresh token issues a new JWT
    - **Property 3: Refresh token issues a new JWT**
    - **Validates: Requirements 1.6**
    - For any valid refresh token from login, assert refresh endpoint returns new valid JWT without re-authentication
    - Implemented: `auth/src/test/java/com/flowdesk/auth/AuthPropertyTest.java#p3_refreshTokenIssuesNewJwt`
  - [x] 2.7 Implement OAuth2 and SAML 2.0 integration
    - Configure Spring Security OAuth2 client for `GET /api/v1/auth/oauth2/callback`
    - Configure Spring Security SAML for `POST /api/v1/auth/saml/acs`
    - Map external identity to internal user/tenant on first login
    - _Requirements: 1.8_
  - [x] 2.8 Implement RBAC enforcement as a Spring Security filter
    - Define roles: `VIEWER`, `MEMBER`, `ADMIN`, `HR_ADMIN`, `MANAGER`, `FINANCE`, `SALES_REP`
    - Annotate all protected endpoints with `@PreAuthorize` or a custom `@RequiresRole` annotation
    - Reject VIEWER write attempts and cross-tenant access with HTTP 403
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_
  - [x] 2.9 Write property test P4: RBAC enforcement is universal across all protected endpoints
    - **Property 4: RBAC enforcement is universal across all protected endpoints**
    - **Validates: Requirements 2.2, 2.5, 2.6**
    - Generate endpoint × role matrix; assert any role lacking required permission receives 403; assert VIEWER always receives 403 on write operations
    - Implemented: `auth/src/test/java/com/flowdesk/auth/RbacPropertyTest.java`

- [x] 3. Checkpoint — auth tests written and passing.

- [x] 4. Implement Audit Log and Transactional Outbox (core infrastructure)
  - [x] 4.1 Implement immutable audit log writer
    - Create `AuditLogService` that inserts into `core_schema.audit_log` with actor, action, entity type, entity ID, timestamp, before/after snapshots
    - Expose as a Spring bean consumed by all modules via an `@AuditLog` AOP aspect on service methods
    - Verify UPDATE/DELETE are revoked on the audit_log table in migration
    - _Requirements: 13.3, 13.4_
  - [x] 4.2 Write property test P15: Audit log completeness and immutability
    - Implemented: `core/src/test/java/com/flowdesk/core/audit/AuditLogPropertyTest.java`
  - [x] 4.3 Implement transactional outbox per module schema
  - [x] 4.4 Write property test P14: Transactional outbox atomicity
    - Implemented: `core/src/test/java/com/flowdesk/core/outbox/OutboxPropertyTest.java`

- [x] 5. Implement two-level cache (Caffeine L1 + Redis L2)
  - [x] 5.1 Implement `CacheService` with L1 → L2 → DB lookup chain
  - [x] 5.2 Implement cache invalidation on entity writes
  - [x] 5.3 Expose cache metrics to Prometheus
  - [x] 5.4 Write property test P11: Cache lookup order is L1 → L2 → database
    - Implemented: `core/src/test/java/com/flowdesk/core/cache/CachePropertyTest.java`
  - [x] 5.5 Write property test P12: Cache invalidation is consistent after writes
    - Implemented: `core/src/test/java/com/flowdesk/core/cache/CachePropertyTest.java`

- [x] 6. Implement Kafka event bus and dead-letter routing
  - [x] 6.1 Configure Kafka topics and consumer groups
  - [x] 6.2 Implement consumer retry and dead-letter routing
  - [x] 6.3 Write property test P13: Dead-letter routing after exhausted retries
    - Implemented: `core/src/test/java/com/flowdesk/core/kafka/KafkaDlqPropertyTest.java`

- [x] 7. Implement Task Module
  - [x] 7.1 Implement project CRUD
  - [x] 7.2 Implement task CRUD with soft-delete
  - [x] 7.3 Write property test P5: Resource creation is a round-trip
    - Implemented: `task/src/test/java/com/flowdesk/task/TaskPropertyTest.java`
  - [x] 7.4 Write property test P6: Task updates are reflected in subsequent reads
    - Implemented: `task/src/test/java/com/flowdesk/task/TaskPropertyTest.java`
  - [x] 7.5 Implement task assignment with cross-tenant validation
  - [x] 7.6 Write property test P8: Cross-tenant assignment is rejected
    - Implemented: `task/src/test/java/com/flowdesk/task/TaskPropertyTest.java`

- [x] 8. Checkpoint — Task module tests written.

- [x] 9. Implement tenant isolation enforcement
  - [x] 9.1 Add tenant filter to all module repository queries
  - [x] 9.2 Write property test P7: Tenant isolation — queries never return cross-tenant data
    - Implemented: `task/src/test/java/com/flowdesk/task/TaskPropertyTest.java`
  - [x] 9.3 Enable PostgreSQL Row-Level Security (RLS) on all tenant-scoped tables
    - Implemented: `core/src/main/resources/db/migration/V9__enable_rls.sql`
  - [x] 9.4 Set `app.tenant_id` session variable on every DB connection
    - Implemented: `core/src/main/java/com/flowdesk/core/context/TenantSessionInterceptor.java`

- [x] 10. Implement HR Module
  - [x] 10.1 Implement employee record management
  - [x] 10.2 Implement attendance recording
  - [x] 10.3 Implement payroll run calculation
  - [x] 10.4 Write property test P20: Payroll calculation correctness
    - Implemented: `hr/src/test/java/com/flowdesk/hr/HrPropertyTest.java`
  - [x] 10.5 Implement performance reviews

- [x] 11. Implement Inventory Module
  - [x] 11.1 Implement SKU and stock management
  - [x] 11.2 Write property test P19: Low-stock events are published for any threshold-crossing transaction
    - Implemented: `inventory/src/test/java/com/flowdesk/inventory/InventoryPropertyTest.java`
  - [x] 11.3 Implement purchase orders with line item validation
  - [x] 11.4 Implement supplier records and warehouse management

- [x] 12. Implement Accounting Module
  - [x] 12.1 Implement double-entry journal entries
  - [x] 12.2 Write property test P9: Double-entry ledger invariant
    - Implemented: `accounting/src/test/java/com/flowdesk/accounting/AccountingPropertyTest.java`
  - [x] 12.3 Implement account balance queries and financial reports
  - [x] 12.4 Implement AP/AR invoice tracking
  - [x] 12.5 Implement budget tracking

- [x] 13. Checkpoint — Ensure accounting and inventory tests pass, ask the user if questions arise.

- [x] 14. Implement Sales Module
  - [x] 14.1 Implement customer record management
    - Create `POST /api/v1/sales/customers` persisting to `sales_schema.customers` with credit limit and payment terms
    - _Requirements: 8.1_
  - [x] 14.2 Implement opportunity pipeline with closed-won automation
    - Create `POST /api/v1/sales/opportunities` and `PUT /api/v1/sales/opportunities/{id}`
    - On transition to `CLOSED_WON`, automatically create a linked sales order within 5 seconds (async via application event or scheduled task)
    - _Requirements: 8.2, 8.3_
  - [x] 14.3 Write property test P18: Opportunity closed-won triggers order creation
    - **Property 18: Opportunity closed-won triggers order creation**
    - **Validates: Requirements 8.3**
    - For any opportunity transitioned to CLOSED_WON, assert linked sales order is created within 5 seconds
  - [x] 14.4 Implement sales orders with credit hold logic
    - Create `POST /api/v1/sales/orders`, `PUT /api/v1/sales/orders/{id}/confirm`, `POST /api/v1/sales/orders/{id}/invoice`
    - On confirm, publish `sales.order.confirmed` event via outbox within 500ms
    - Check customer outstanding balance + order value against credit limit; if exceeded, set `credit_hold = true` and publish `sales.credit-hold` event
    - _Requirements: 8.4, 8.5, 8.6, 8.7_
  - [x] 14.5 Write property test P17: Sales credit hold is applied for any order exceeding credit limit
    - **Property 17: Sales credit hold is applied for any order exceeding credit limit**
    - **Validates: Requirements 8.7**
    - For any order where outstanding balance + order value > credit limit, assert order is placed on credit hold and notification event is published
  - [x] 14.6 Implement customer interaction recording
    - Create `POST /api/v1/sales/interactions` persisting calls, emails, meetings linked to customer or opportunity with timestamp and author
    - _Requirements: 8.8_

- [x] 15. Implement Reporting Module and Elasticsearch integration
  - [x] 15.1 Implement pre-built dashboards per module
    - Create `GET /api/v1/reporting/dashboards/{module}` returning key metrics refreshed at <= 5 minutes
    - Cache dashboard results in Redis with 5-minute TTL
    - _Requirements: 9.1, 18.1, 18.2_
  - [x] 15.2 Implement custom report definition and execution
    - Create `POST /api/v1/reporting/reports` (define report) and `POST /api/v1/reporting/reports/{id}/execute`
    - Execute against read replicas; return results within 10 seconds for up to 100,000 rows
    - Enforce RBAC: filter results to only resources the requesting user is authorized to view
    - _Requirements: 9.2, 9.3, 9.7_
  - [x] 15.3 Implement async data export (CSV/XLSX)
    - Create `GET /api/v1/reporting/reports/{id}/export`
    - For result sets > 10,000 rows, process asynchronously and notify user via event when file is ready; store export in S3
    - _Requirements: 9.4, 9.5_
  - [x] 15.4 Implement Elasticsearch full-text search
    - Configure Elasticsearch client and index mappings for all module entities
    - Create `GET /api/v1/reporting/search?q=...` returning results within 500ms for indexed datasets
    - Sync data to Elasticsearch via Kafka consumer on entity change events
    - _Requirements: 9.6_
  - [x] 15.5 Enforce max sync query size and async-only threshold
    - Reject synchronous report execution requests for datasets exceeding 10,000 rows; return HTTP 422 with message "Result set too large — use async export"
    - Route all requests above the threshold to the async export flow (task 15.3)
    - _Requirements: 9.3, 9.5_
  - [x] 15.6 Implement cursor-based pagination for large result sets
    - Replace offset-based pagination in `POST /api/v1/reporting/reports/{id}/execute` with keyset/cursor pagination
    - Accept `cursor` query parameter; return `nextCursor` in response envelope; default page size 1,000 rows
    - Prevents full-table scans and keeps P95 latency within SLO under large datasets
    - _Requirements: 9.3, 22.1_

- [x] 16. Implement API Gateway layer
  - [x] 16.1 Implement sliding window rate limiter using Redis
    - Implement `RateLimitFilter` using Redis with key `rate:{userId}` (100 req/min) and `rate:ip:{ip}` (20 req/min for unauthenticated)
    - Return 429 with `Retry-After` header on limit exceeded
    - TTL on Redis key equals window duration (60 seconds)
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_
  - [x] 16.2 Write property test P10: Rate limiting enforces sliding window with correct HTTP response
    - **Property 10: Rate limiting enforces sliding window with correct HTTP response**
    - **Validates: Requirements 10.1, 10.3, 10.4**
    - Generate request bursts exceeding 100/min; assert all requests beyond limit receive 429 with Retry-After; assert sliding window correctly counts at boundaries
  - [x] 16.3 Configure versioned REST endpoints and OpenAPI spec
    - Ensure all endpoints follow `/api/v1/` pattern
    - Configure SpringDoc OpenAPI 3.0 to auto-generate spec from annotations, kept in sync with implementation
    - _Requirements: 10.6, 10.7_
  - [x] 16.4 Implement central request routing and JWT validation at gateway level
    - Implement a `GatewayRoutingFilter` (Spring Cloud Gateway or custom `OncePerRequestFilter`) that routes requests to the correct module handler based on path prefix
    - Move JWT validation out of individual modules into a single gateway-level filter so auth is enforced uniformly before any module code executes
    - Return 401 for missing/invalid/expired JWT before the request reaches module logic
    - _Requirements: 2.5, 2.6, 10.6_
  - [x] 16.5 Inject Correlation ID into every request
    - In the gateway filter chain, read `X-Correlation-ID` header if present; otherwise generate a UUID
    - Store in `MDC` (Mapped Diagnostic Context) so it appears in every log line for that request
    - Forward the header downstream and include it in all API responses
    - _Requirements: 17.1, 17.3_
  - [x] 16.6 Add gateway-level request logging and metrics
    - Log every inbound request at INFO level: method, path, tenant ID, correlation ID, response status, and latency
    - Register Micrometer meters at the gateway filter: `http.server.requests` tagged by module, method, and status
    - _Requirements: 17.1, 17.4_

- [x] 17. Implement idempotency keys for mutating POST APIs
  - [x] 17.1 Accept and validate `Idempotency-Key` header on critical POST endpoints
    - Require the `Idempotency-Key` header (UUID) on: `POST /api/v1/sales/orders`, `POST /api/v1/accounting/ap/invoices`, `POST /api/v1/accounting/ar/invoices`, `PUT /api/v1/inventory/skus/{id}/stock`, and `POST /api/v1/hr/payroll/run`
    - Return 400 if the header is missing on these endpoints
    - _Requirements: 8.4, 6.1, 5.4_
  - [x] 17.2 Store request hash and response in Redis; return cached response on retry
    - On first execution, store `idempotency:{key}` → `{ requestHash, responseStatus, responseBody }` in Redis with a 24-hour TTL
    - On subsequent requests with the same key, verify the request hash matches; if so, return the cached response immediately without re-executing business logic
    - If the hash differs (same key, different body), return 422 with message "Idempotency key reused with different request body"
    - Prevents duplicate orders, double stock deductions, and duplicate payroll runs
    - _Requirements: 8.4, 6.1, 5.4, 22.2_

- [x] 18. Implement distributed locking for critical concurrent flows
  - [x] 18.1 Integrate Redisson (or Redis SETNX) distributed lock client
    - Add Redisson dependency; configure `RedissonClient` bean pointing at the existing Redis Cluster
    - Implement a `DistributedLockService` with `tryLock(key, ttl)` / `unlock(key)` methods wrapping `RLock`
    - _Requirements: 22.2_
  - [x] 18.2 Apply distributed locks to payroll runs, order confirmation, and stock updates
    - Payroll run: acquire lock `lock:payroll:{tenantId}:{payPeriod}` before executing; reject with 409 if lock is held (concurrent run in progress)
    - Order confirmation: acquire lock `lock:order:{orderId}` before state transition to prevent double-confirm
    - Stock updates: acquire lock `lock:stock:{skuId}:{warehouseId}` before adjusting `quantity_on_hand` (complements optimistic locking)
    - Release all locks in a `finally` block; set TTL to 30 seconds as a safety net against crash-without-release
    - _Requirements: 5.4, 8.4, 6.1, 22.2_

- [x] 19. Checkpoint — Ensure all module tests pass, ask the user if questions arise.

- [x] 20. Implement observability (Prometheus, Grafana, ELK, Jaeger)
  - [x] 20.1 Configure Micrometer + Prometheus metrics endpoint
  - [x] 20.2 Configure Jaeger distributed tracing
  - [x] 20.3 Implement `/health` endpoint
  - [x] 20.4 Configure slow query logging

- [x] 21. Set up Docker Compose for local development
  - [x] 21.1 Write `docker-compose.yml` with all services
  - [x] 21.2 Write multi-stage Dockerfiles for backend and frontend

- [x] 22. Write Terraform IaC for AWS infrastructure
  - [x] 22.1 Define EKS cluster, RDS, ElastiCache, MSK, ECR, S3 in Terraform
  - [x] 22.2 Define Kubernetes manifests (Deployments, HPA, NetworkPolicies)
  - [x] 22.3 Configure multi-region failover for RTO ≤ 15 minutes
  - [x] 22.4 Configure S3 cross-region replication and Route 53 failover

- [x] 23. Set up GitHub Actions CI/CD pipeline
  - [x] 23.1 Implement PR validation workflow
  - [x] 23.2 Implement main branch build and push workflow
  - [x] 23.3 Implement blue-green deployment workflow

- [x] 24. Implement React frontend
  - [x] 24.1 Initialize React SPA with route-based code splitting
  - [x] 24.2 Implement authentication UI and JWT handling
  - [x] 24.3 Implement Task module UI
  - [x] 24.4 Implement HR module UI
  - [x] 24.5 Implement Inventory module UI
  - [x] 24.6 Implement Accounting module UI
  - [x] 24.7 Implement Sales module UI
  - [x] 24.8 Implement Reporting and Analytics dashboard UI
  - [x] 24.9 Configure frontend error reporting and Lighthouse CI
  - [x] 24.10 Integrate React Query for server state management
  - [x] 24.11 Integrate Zustand for client-side state management

- [x] 25. Implement feature flags
  - [x] 25.1 Implement feature flag storage and evaluation
  - [x] 25.2 Expose feature flag admin API and integrate at call sites

- [x] 26. Implement data retention and archival
  - [x] 26.1 Archive audit logs older than 1 year to S3
  - [x] 26.2 Implement soft-delete cleanup jobs
  - [x] 26.3 Implement GDPR delete workflow

- [x] 27. Implement backpressure mechanisms
  - [x] 27.1 Monitor Kafka consumer lag and reject requests when overloaded
  - [x] 27.2 Integrate Resilience4j circuit breakers on external dependency calls

- [x] 28. Final checkpoint — Ensure all tests pass and all components are wired together, ask the user if questions arise.

## Optional Advanced Tasks

- [ ] A1. CQRS for Reporting module
  - Implement separate write model (PostgreSQL) and read model (Elasticsearch or materialized views) for the Reporting module
  - Write path: domain events update the read model asynchronously via Kafka consumers
  - Read path: all `GET /api/v1/reporting/...` queries hit the read model only

- [ ] A2. Event versioning for Kafka schemas
  - Add a `version` integer field to all Kafka event schemas (default: 1)
  - Implement a `VersionedEventDeserializer` that routes to the correct handler based on version
  - Document upgrade path for each event type when schema changes are needed

- [ ] A3. Schema Registry enforcement for Kafka topics
  - Configure Confluent Schema Registry (or AWS Glue Schema Registry) for all Kafka topics
  - Register Avro or JSON Schema definitions for every event type
  - Configure producers to reject publishes that fail schema validation; configure consumers to reject messages that fail deserialization

- [ ] A4. Chaos testing
  - Implement chaos test scenarios using Testcontainers or a chaos engineering tool (e.g., Chaos Monkey for Spring Boot)
  - Scenarios: Redis unavailable → verify L1 fallback; Kafka broker down → verify outbox relay queues events; DB primary failover → verify read replicas serve reads; circuit breaker open → verify 503 with Retry-After
  - Run chaos tests in a dedicated CI stage (not blocking production deploy)

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- All 20 correctness properties are covered by property-based tests using jqwik (tagged with `@Tag("Feature: saas-platform, Property N: ...")`)
- Property tests run a minimum of 100 iterations with randomized inputs
- Checkpoints ensure incremental validation at logical boundaries
- All secrets must be loaded from AWS Secrets Manager — never hardcoded
- TLS 1.3 must be enforced at the ALB/Nginx layer; HTTP → HTTPS redirect returns 301
