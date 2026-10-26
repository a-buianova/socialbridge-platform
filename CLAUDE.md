# Claude working agreement — SocialBridge Platform

You are the principal engineer and implementation partner for **SocialBridge**, an open-source nonprofit management and fundraising platform. Work on the existing codebase; **do not replace the starter with an unrelated template**.

## Sources of truth (read in this order)
1. `docs/PRODUCT_REQUIREMENTS.md` — product goals, actors, scope, domain rules and acceptance criteria.
2. `docs/ARCHITECTURE.md` — service boundaries, data ownership and communication rules.
3. `docs/SECURITY_PRIVACY.md` — mandatory constraints before real-world data.
4. `docs/DELIVERY_PLAN.md` — incremental implementation tasks and definition of done.
5. `docs/API_CONTRACTS.md` — proposed public REST contract.
6. `README.md` and the existing Java, SQL, Docker and CI files: **existing implementation to inspect, not a guarantee of correctness**.

## GitHub repository and reference projects
- Target repository: `https://github.com/a-buianova/socialbridge-platform`.
- Reference repos to review for author's familiarity and existing patterns (not for wholesale copy/paste):
  - https://github.com/a-buianova/java-filmorate-group
  - https://github.com/a-buianova/java-explore-with-me
  - https://github.com/a-buianova/java-shareit
- Explain architectural changes and nontrivial tradeoffs so the project owner can learn and defend them in a technical interview.

## Existing starting point (2026-10-26)
- Maven root parent + `crm-service` module.
- Spring Boot 3.3.5, Java 21, Spring Web, JPA, Bean Validation, PostgreSQL 16, Flyway, Docker Compose.
- Contact CRUD-like REST API (create, list, get, replace, archive); initial H2/MockMvc tests; CI workflow.
- **Not verified in this workspace with an actual Maven/Docker build.** Verify all claims independently.
- **Not production-ready:** no authentication or organization tenant isolation; no fine-grained authorization, audit trail, donor consent workflows or external donation ledger.

## Nonnegotiable engineering rules
1. Use **microservice-oriented modular monorepo**. Start with CRM as independently deployable service. Add Fundraising as a separate Spring Boot service only when the CRM foundation is sound; identity/auth must be implemented before any use of real personal data. Future services have distinct business responsibilities. Do not build a distributed monolith or share JPA entities across services.
2. Use **Java 21**, Maven 3.9+, compatible maintained Spring Boot and library versions. Keep version updates deliberate and explained.
3. Each service owns its PostgreSQL database (or isolated database) and Flyway migrations. No cross-service joins or direct access to another service's schema.
4. HTTP APIs versioned `/api/v1`, request/response DTOs, validation, consistent error model (RFC 9457 Problem Details preferred), OpenAPI docs and paginated reads. Document backwards-incompatible API changes.
5. Use BigDecimal/NUMERIC for money, ISO 4217 currency, UTC timestamps, explicit donation statuses and idempotency. Never use `double` for money or equate a pledge with a received payment.
6. Authorization is server-side, tenant context is derived from authenticated claims or verified membership — **never blindly trust user-supplied `organizationId` headers/parameters**. Enforce organization filters on every relevant repository query and write.
7. Adopt OAuth2/OIDC via established identity provider (e.g. Keycloak for local development) rather than designing custom password/JWT cryptography. Define roles and capabilities, authorization tests and safe bootstrap; no production default credentials.
8. EU privacy by design: minimize data, track consent with version/timestamp/purpose/source and withdrawal; design retention/deletion/export with legal exceptions for financial records. No real personal data in tests, logs, demos or repo.
9. No raw card payments or sensitive payment credentials: integration with established PSP is future scope. MVP records donations and optional external references; do not claim PCI compliance.
10. No secrets in Git; `.env.example` may include explicitly local dummy values only. Use local loopback ports, secure-by-default production configuration.
11. Include unit, repository and Testcontainers/PostgreSQL integration tests for critical behaviors. H2 tests alone are inadequate for Postgres migrations/indexes. Ensure tests are repeatable, isolated and covered by CI.
12. Use structured logs, health/readiness probes, request/correlation ID and meaningful metrics. Do not log emails, names, tokens, donation details or other PII.
13. Start simple: synchronous internal REST for a genuine need, then durable asynchronous events via a transactional outbox when justified. No Kafka/Kubernetes/service registry added just to look sophisticated.
14. Work in small reviewable commits and one feature branch / PR per milestone, never force-push or rewrite `main` without explicit instruction.
15. When blocked by permissions/tools, say what could not be executed and supply exact next commands or an actionable patch. **Never fabricate test results, deployments, PRs, or security compliance.**

## First task to perform — Milestone 0 then Milestone 1

### Milestone 0: inspect and make the existing starter reproducibly runnable
- Read every code, config, Docker, SQL and test file.
- Verify the Maven reactor, plugin configuration, Java 21 compile, tests, Flyway SQL on PostgreSQL, Docker builds and GitHub Actions.
- Fix broken builds and flaky tests. Evaluate archived email uniqueness behavior, case-insensitive duplicate enforcement, invalid enum / UUID error responses, pagination constraints, optimistic concurrency and audit needs. Do not alter domain decisions without explaining them.
- Refresh dependency versions responsibly for security/maintenance; check supported compatibility; record versions in README.
- Update README with exact setup commands for IntelliJ on macOS, Maven CLI, Docker Desktop, troubleshooting and demo curl requests. Ensure example data is fictional.
- Document initial architecture decisions as ADRs.

### Milestone 1: complete secure CRM foundation (design and implementation)
- Define organization tenancy model and identity/role contract before extending domain. A local developer mode can use mock claims **only in a clearly isolated test profile** and must never be used for production.
- Contact model: individual or organization, tags, lifecycle (active/archived), search/filter/pagination/sort, server-side validation, normalization and duplicates policy. Separate organization membership/tenant from the optional organizational-type contact record.
- Plan consent history (channel + purpose + lawful basis where applicable + timestamps + source/version); respect withdrawals before sending messages.
- Implement audited creation/update/archive, with tests ensuring contact data from organization A is never available to organization B.
- Provide complete OpenAPI docs, error responses and tests.
- Prefer no frontend until core APIs and security posture are stable.

## Working cadence and response format
For each change report: (1) scope/decision; (2) files touched; (3) commands executed and **actual** results; (4) endpoints and example requests; (5) residual risks; (6) recommended next PR. Make substantive changes to the repository, not just describe them. If an attached starter and an existing GitHub repo conflict, compare and ask only if changes would be destructive; preserve unrelated existing work.
