# Delivery roadmap and Definition of Done

## Working method
Develop vertical slices through separate Git branches and reviewable pull requests. For each PR: state business value, contract changes, migration impact, tests, security impact, how to run, remaining limitations, and status of checks. Keep `main` buildable. Do not conflate "merged" with "production deployed".

## M0 — Baseline audit and reproducibility (first Claude assignment)
Deliverables:
- Import **provided starter** to `a-buianova/socialbridge-platform` if the repo is empty, preserving file contents and history of any existing work. First commit should identify it as initial prototype, not production-ready.
- Validate Java 21 Maven reactor, build/test commands, Docker multi-stage image, Postgres/Flyway migration, CI, local ports and README.
- Fix defects discovered; run Postgres-backed integration tests (Testcontainers) for migrations and unique index; report exact outcomes.
- Identify/resolve existing email uniqueness behavior for archived records and H2/Postgres mismatch with explicit test cases.
- Add credible formatting/linting/dependency-health checks as appropriate; no meaningless 100% coverage pledge.
- Publish ADR on modular microservice monorepo and service data ownership.

Acceptance:
1. New developer can clone and follow README on JDK21 + Docker Desktop to start CRM and GET health.
2. `mvn -B verify` passes in CI; PostgreSQL/Flyway-tested CRUD + 400/404/409 cases in integration suite.
3. Only fictional demo data; no secrets committed; README warns about unprotected API.

## M1 — Tenant / identity contract and secure CRM
Deliverables:
- Explicit organization domain, verified membership/roles, Spring Security/OIDC integration, local Keycloak/dev setup (or documented equivalent).
- Safe tenant resolution and authorization; consistent 401/403/404 semantics, tests blocking cross-org read/list/update/archive.
- Evolve contacts to person/organization, multiple categories and tags, optional email, meaningful search/filters, optimistic locking and audit logs.
- Consent domain design and append-only consent/withdrawal history; documentation and tests.

Acceptance:
1. Org A user cannot access org B data even by knowing its UUID.
2. Unauthenticated calls are blocked except explicitly public health and docs as configured.
3. Contact records follow tenant-aware uniqueness; consent not assumed from creation.
4. API contract and migrations tested on PostgreSQL.

## M2 — Fundraising service and business value
Deliverables:
- Independent Spring Boot service and PostgreSQL DB, Flyway, Docker and CI.
- Campaign create/update/list/status transitions; donation recording via REST; idempotency and financial status transitions; BigDecimal precision and currency-specific reports.
- Donor reference to CRM via authorized internal API, no cross-DB SQL; resilience/failure modes, tests.

Acceptance:
1. Fundraiser creates campaign and records received donation for their org; report shows correct EUR total.
2. Exact same request with same idempotency key does not duplicate donation; changed payload with reused key fails predictably.
3. Pledges do not count as received; refunds have explicit verified semantics.
4. Org A cannot report or modify org B donations.

## M3 — Communication and messaging
Deliverables:
- Templates, consent-aware segments, transactional outbox, RabbitMQ when justified, retry-safe delivery workers, unsubscribe.
- Provider sandbox; outbound real email disabled by default; auditing, tests and DLQ monitoring.

Acceptance: withdrawn contact never receives a newly queued message; retries do not cause duplicate send under normal documented guarantees.

## M4 — Dashboard / volunteers / events / impact
Deliverables:
- Accessible admin dashboard, org KPIs, volunteer scheduling and event registration, project/grant tracking and limited exports based on evidence of nonprofit priorities.
- Performance measurements, security reviews, documentation and deployment runbooks.

## Global Definition of Done
- Executable feature, not pseudo-code; consistent formatting and dependencies.
- Documented domain decisions and relevant ADRs; OpenAPI updated.
- New behavior unit and integration tested; critical persistence tested against Postgres.
- CI passing, setup verified, data migration safe, tenant isolation tested.
- Logging avoids PII/secrets; no unprotected production endpoints; permission checks verified.
- Documentation accurate about what is implemented vs planned.
- PR summary includes changed files, commands/results, residual risks and follow-up issues.
