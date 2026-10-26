# First message to Claude / Claude Code — SocialBridge

You are my senior Java backend engineer, software architect and technical mentor. I want us to **build a real, maintainable open-source platform**, not a disposable tutorial or a visual mockup. Please communicate implementation summaries and technical explanations to me in **Russian**; keep source code, API naming, technical documentation, commits, and PR descriptions in **English**.

## Product and motivation
**SocialBridge** is an open-source CRM and fundraising platform for small/medium nonprofit organizations, foundations, volunteer initiatives and community projects, initially oriented toward the EU/Germany. Many NGOs currently split workflows across spreadsheets, email marketing tools, fundraising databases, event registration platforms and accounting exports. SocialBridge should progressively provide one dependable system to manage: supporter/donor/volunteer/partner contacts, consent and engagement, fundraising campaigns and donation records, analytics and reports, communication campaigns, volunteer coordination and events, later projects/grants/impact reporting. The goal is real utility for NGO teams, not just passing automated tests.

I have substantial nonprofit/fundraising/project-management experience and a Java backend educational background. My earlier Java projects are:
- https://github.com/a-buianova/java-filmorate-group
- https://github.com/a-buianova/java-explore-with-me
- https://github.com/a-buianova/java-shareit

Inspect those repos to understand my Java level, Spring Boot / Maven / JPA / PostgreSQL / Docker / testing patterns. They are references, not code to copy blindly. Explain nontrivial choices and tradeoffs so I can understand and extend the code in IntelliJ IDEA.

## Target repository and attached source
**GitHub repo:** https://github.com/a-buianova/socialbridge-platform

I am attaching a ZIP called `socialbridge-claude-handoff.zip`. It contains `socialbridge-platform/`: **our existing scaffold**, which you must inspect and build on. It currently includes a Maven parent, `crm-service`, Spring Boot 3.3.5, Java 21, REST contact CRUD, JPA/PostgreSQL 16, Flyway migration, Docker Compose, initial tests and GitHub Actions. It is an early prototype and has NOT been fully verified through an actual Maven/Docker build in the environment that generated it. It has NO auth/tenant isolation and must not store real contact data. Do not treat it as production-ready or discard/rewrite it without a justified migration plan.

After extracting, read the repository root `CLAUDE.md` and every document in `docs/` **before implementation**. Treat `docs/PRODUCT_REQUIREMENTS.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md`, `docs/API_CONTRACTS.md`, and `docs/DELIVERY_PLAN.md` as detailed product and engineering requirements. If the GitHub repository is still empty, import this starter there (on a suitable branch, preserving any existing changes); if GitHub operations aren't available, prepare ready-to-apply commits/patches and give me exact commands. Do not pretend it was pushed when it wasn't.

## Technology and architecture
- Java 21, maintained compatible Spring Boot 3.x/4.x version supported by chosen libraries, Maven 3.9+, Spring Web, Spring Data JPA/Hibernate, Jakarta Bean Validation, PostgreSQL, Flyway, Docker/Compose, JUnit 5, Mockito, Testcontainers, OpenAPI, GitHub Actions, Spring Security with standards-based OIDC.
- **Microservice architecture in a modular monorepo**, with a separately deployable CRM Service now and Fundraising Service next. Identity/membership/OIDC integration and API gateway/communication service will be added with clear business justification; do not create five empty skeletons solely for appearance. Each domain service owns its own schema/database and migrations. No cross-service SQL or shared JPA entities.
- Use narrow REST service integration initially; introduce RabbitMQ + durable outbox only when asynchronous workflows justify it. Keep architecture realistic for a small nonprofit and a solo developer.
- Clear controller / DTO / validation / service / repository / mapper layers, consistent error responses, deterministic pagination, meaningful domain state transitions, transactional integrity, idempotency where required, tests against actual PostgreSQL, safe configuration and observable operations.

## Critical product/business rules
- True multi-tenant organization isolation with server-validated membership/role; never rely on caller-provided org IDs or guessable contact IDs to authorize access.
- Contact can be an individual or organization, with multiple relationship categories, optional email for offline donations, tags, activity history, archival and potential duplicates policy.
- Communication consent must be purpose- and channel-specific; contact creation or donation does NOT automatically authorize promotional outreach. Record verifiable consent/withdrawal history.
- Fundraising supports campaigns and explicitly differentiated PLEDGED, RECEIVED, REFUNDED/VOIDED states; precise BigDecimal money, currency ISO 4217, recurring donation pledges later, retries/idempotency, an audit trail, and separate aggregation by currency. Do not implement real credit-card processing; external PSP integration is a later, separately secured feature.
- EU privacy by design: minimized data, GDPR-related retention/export/correction/deletion workflows with proper handling of financial-record retention, role-based access, no PII in logs, fake test records only, no misleading compliance claims.
- Dashboard, emails, volunteer management, events, grant/project management and impact metrics are later milestones. Do not delay foundational security/testing to build a flashy frontend.

## WHAT I WANT YOU TO DO RIGHT NOW: milestones 0 and first reviewable slice
1. Explore the attached files and target repo, summarize exactly what exists, what builds, what fails, and what is only planned. Identify architecture flaws, technical debt and security blockers. Do not claim results of commands you haven't run.
2. If the repo is empty, initialize it using our provided starter project and new `CLAUDE.md` + `docs/` specifications. Keep everything under the repository root, not in a redundant nested directory. Use a feature branch/PR when possible; no force pushes or destructive overwrites.
3. Get the **current `crm-service` to build, test and run reproducibly** using JDK21, Maven and PostgreSQL. Run `mvn -B verify` and, if available, `docker compose up --build -d`, check readiness and test its endpoints. Fix real failures you discover.
4. Improve the first CRM slice: consistent validation/error handling, normalized emails, race-safe duplicate detection policy, deterministic pagination, archival semantics, robust tests. Test Flyway migrations/constraints against PostgreSQL with Testcontainers; H2-only tests are not sufficient. Keep README and CI accurate.
5. Propose a short technical ADR and incremental implementation plan for secure tenant isolation/OIDC before any real donor records. Do not silently expose unauthenticated demo endpoints to the internet.
6. Create real code changes, tests and documentation. If GitHub/CLI privileges permit, open one reviewable PR and give its link. If you cannot operate GitHub, provide exact local steps and a patch. Tell me clearly what remains undone.

## Definition of done for the first task
- The provided starter is preserved and improved, not replaced with a toy example.
- A new developer can clone it and run CRM locally with Java 21 and Docker Desktop following README.
- `mvn -B verify` and PostgreSQL integration tests succeed where tools allow; if blocked, explicitly report which tests were not run and why.
- CI configuration is valid; no credentials/private data; schema changes through Flyway.
- API request examples demonstrate fictional create/get/list/update/archive behavior and error cases.
- A precise next-step plan exists for authenticated multi-tenant CRM and fundraising service.

## Please format your reply in Russian
1. What you found in the starter/repositories.
2. Proposed immediate changes and architecture decisions.
3. The work actually completed (files/commits/PR).
4. Commands run and their actual results.
5. How I can launch/check it in IntelliJ IDEA on macOS.
6. Remaining risks and the **single next milestone**.

Be an engineering partner: challenge questionable requirements, propose simpler approaches when justified, but always prioritize real implementation, correctness, maintainability, and learning over empty complexity.
