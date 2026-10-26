# Initial prototype audit

**Scope:** the supplied handoff starter, preserved by the initial prototype import commit. This document separates observed baseline defects from planned product features. It does not certify production readiness.

## What existed

The GitHub remote advertised no commits or `main` branch when onboarding began; existing Git authentication succeeded. The supplied ZIP provided a Maven parent and one `crm-service`, Spring Boot 3.3.5, Java 21, a PostgreSQL 16 Compose database, one Flyway migration, contact create/get/list/replace/archive endpoints, two H2/MockMvc tests and a GitHub Actions Maven workflow. There were no identity, organization, consent, fundraising or frontend implementations.

## Baseline findings and first-slice decisions

| Finding in the supplied source | Resulting action / limit |
| --- | --- |
| `ContactController` references `BadRequestException` without importing its different package | Fix compilation and make request/path parameter names explicit |
| Compiler parameter metadata is not enabled | Use explicit controller annotation names and supported compiler configuration |
| Tests use H2 with Flyway disabled | Require PostgreSQL/Testcontainers tests that apply real migrations and validate mappings |
| Email is mandatory and normalization occurs after request validation | Normalize before validation and allow absent email for offline contacts |
| Database uniqueness includes archived emails, but the policy is undocumented | Preserve and test reservation; document its temporary global scope in ADR-0002 |
| All integrity violations become duplicate-email conflicts | Translate only the known email index; preserve unrelated failures for diagnosis |
| Concurrent update/archive can overwrite state without a row version | Add an internal optimistic version through a forward migration; document HTTP stale-client limits |
| Lists sort only on creation time and expose Spring Data serialization | Add an ID tie breaker and an explicit page contract |
| Error maps vary from the proposed Problem Details design | Use safe RFC 9457 errors, field messages without rejected values and request correlation |
| Native application binding does not explicitly restrict its interface | Default native binding to loopback; keep Compose published ports on host loopback |
| No authentication, authorization, tenant isolation, consent or complete audit/privacy workflow | Keep synthetic-data-only operation explicit; plan the next security milestone rather than claiming these features exist |

The dependency update to Boot 4.1.2 and matching springdoc 3.2.0 is deliberate: the original prototype was unverified, and its 3.3.5 baseline was old. Java 21, Maven, layered Spring/JPA code and the existing contact routes are retained. Dependency alignment comes from the Boot parent instead of independently upgrading framework components.

## Execution evidence

The following checks ran in the cloud workspace on 2026-10-26. The final application source tested is commit `6dc103b`; subsequent documentation changes do not change that application source.

| Target and command | Observed result |
| --- | --- |
| Exact supplied starter: `mvn -B verify` | Compilation fails on the missing `BadRequestException` symbol; **zero tests execute** |
| Minimally repaired starter baseline: `mvn -B verify` | Passes its two original H2 tests; this provides a buildable PR base but does not validate PostgreSQL migrations |
| Improved CRM: `mvn -B verify` | **42 tests execute, with 0 failures, 0 errors and 0 skipped**: 34 PostgreSQL-backed API/persistence cases and eight service/error-handler unit tests |
| Compose image build and startup: `docker compose -f docker-compose.yml -f /workspace/.tools/socialbridge/compose.cloud.yml up --build -d --wait` | Image builds successfully; PostgreSQL and CRM both become healthy |
| Native startup: `mvn -B -pl crm-service spring-boot:run -Dspring-boot.run.arguments=--server.port=8082` | Application starts against the Compose PostgreSQL database; readiness returns `UP`, and contact GET returns the documented explicit page shape |

The PostgreSQL-backed tests use PostgreSQL **16.15**, apply the actual Flyway migrations and validate the JPA schema. The fresh Surefire XML reports contain 34 cases in `ContactApiTest`, four in `ContactServiceTest` and four in `GlobalExceptionHandlerTest`.

The Compose build in this cloud network used the additional local override `/workspace/.tools/socialbridge/compose.cloud.yml` to supply the platform's proxy settings and trusted CA through the optional BuildKit `maven_settings` and `build_ca` secrets. Ordinary Docker Desktop builds use the README command without that cloud override. TLS and artifact verification remained enabled. The temporary native process on port 8082 was stopped after its checks; its successful startup does not depend on keeping that process alive.

An earlier Maven dependency-resolution failure was an environment proxy setup issue, separate from the confirmed baseline source defect. The toolchain and supported proxy trust configuration were repaired before the source results above were recorded.

These are **local execution results**, not evidence of a remote GitHub Actions run, a merged PR, a published cloud snapshot or a deployment. Re-run `mvn -B verify` and the documented readiness/contact checks after source or environment changes, and inspect the reports from that run.

## Reference-project review

The owner's three Java projects were inspected for familiar patterns, without copying their implementations:

| Reference | Relevant existing experience |
| --- | --- |
| `java-filmorate-group` | Java 21, Boot 3.2.4, Maven modules, JDBC/domain ports and value objects |
| `java-explore-with-me` | Java 21, Boot 3.3.2, JPA, PostgreSQL/Compose, MapStruct and a main/stats service boundary |
| `java-shareit` | Java 21, Boot 3.3.2, DTO/controller/service/repository layers, OpenAPI, Mockito, WebMvc and H2 tests |

These patterns support an incremental Spring/JPA implementation. Educational caller-supplied user headers must not be carried forward as authenticated NGO membership. Declaring Testcontainers dependencies is also different from executing migration-backed PostgreSQL tests.

## Single next milestone

Implement authenticated, tenant-isolated CRM as described in [ADR-0003](adr/0003-tenant-identity-contract.md) and the [implementation plan](IMPLEMENTATION_PLAN.md). No real contact data, Fundraising integration or public deployment should precede that boundary and its negative authorization tests.
