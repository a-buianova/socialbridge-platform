# SocialBridge Platform

Open-source CRM & fundraising platform for nonprofit organizations. **Status: initial development milestone, not production-ready.**

## Architecture and scope

Maven monorepo with independently deployable Spring Boot services. Current implementation: **crm-service** only. Planned: identity-service, fundraising-service, communication-service, API gateway. Each service owns its persistence boundary; future integration via APIs/events, never direct foreign database reads.

The domain is nonprofit-oriented, but **only use fictional/test data** until authentication, tenant isolation, GDPR workflows, authorization, audit logging and security review are in place. The service currently has **NO authentication**.

## Stack

Java 21, Spring Boot 3.3.5, Maven, Spring Web, Jakarta Validation, Spring Data JPA, PostgreSQL 16, Flyway, JUnit 5, MockMvc, Docker Compose, GitHub Actions.

## Quick start — Docker Desktop required

```bash
docker compose up --build -d
docker compose ps
curl http://localhost:8081/actuator/health
```

Use IntelliJ IDEA → **Open** → select the root `socialbridge-platform` folder (containing root `pom.xml`); set Project SDK to JDK 21, import Maven projects. Launch `CrmApplication` **after starting only the database** via `docker compose up -d crm-db`; otherwise, `docker compose up --build -d` already starts the app in Docker, so don't run the same port twice.

```bash
# Example contact (fictional)
curl -i -X POST http://localhost:8081/api/v1/contacts \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ada Example","email":"ada@example.org","type":"DONOR"}'

curl 'http://localhost:8081/api/v1/contacts?type=DONOR&page=0&size=20'
```

Available endpoints:

| Method | Path | Purpose |
|---|---|---|
| POST | /api/v1/contacts | Create contact, 201 |
| GET | /api/v1/contacts | Paginated list, optional `type` |
| GET | /api/v1/contacts/{id} | One contact |
| PUT | /api/v1/contacts/{id} | Replace editable fields |
| DELETE | /api/v1/contacts/{id} | Archive contact, 204 |
| GET | /actuator/health | Health probe |

Valid contact types: DONOR, VOLUNTEER, PARTNER, SUPPORTER. Errors return JSON and appropriate HTTP status. E-mails are normalized to lowercase, checked for duplicates, and protected by DB uniqueness index. Archived contacts no longer appear in API lists.

## Run tests

```bash
mvn verify
```

Integration tests use an H2 compatibility mode for fast offline DB tests; next milestone adds Testcontainers/PostgreSQL integration tests, security and migrations coverage. Test results should be reviewed in CI before use.

## Planned roadmap

1. CRM persistence, validation, pagination, archival — **implemented scaffold**.
2. Testcontainers/PostgreSQL integration tests; robust updating and domain invariants; centralized request tracing.
3. Identity service, organization tenancy and access control before real data.
4. Fundraising service with donations and campaigns, idempotency and data reconciliation.
5. Communication service with lawful consent, email queues and opt-out.
6. Gateway, secure service-to-service auth, tracing, monitoring, backups and restore drills.

## Local security notes

The Docker Compose defaults are **only** for isolated local development. No payment processing is implemented; never upload donor or financial records. Application and DB ports are bound to loopback on the host. Security hardening, dependency upgrades and production configuration are outstanding.

## For Claude / Claude Code collaborators

Begin with [`CLAUDE.md`](CLAUDE.md) and [`CLAUDE_FIRST_MESSAGE.md`](CLAUDE_FIRST_MESSAGE.md). Read the product and architecture specs under [`docs/`](docs/) before changing code. The repository source in this starter is intentionally marked **development-only**, not production-ready.
