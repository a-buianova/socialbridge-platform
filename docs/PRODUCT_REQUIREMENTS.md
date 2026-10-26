# SocialBridge — Product Requirements Document (PRD)

**Status:** product vision and implementation specification (not an assertion of released functionality)  
**Audience:** engineering, product owner, prospective nonprofit pilot partners  
**Primary geography:** European Union, starting with small/medium-size nonprofits in Germany  
**Working language:** English documentation and APIs; design for localization (EN/DE/RU possible later).

## 1. Product vision
SocialBridge is an open-source, extensible operations platform for nonprofits, grassroots initiatives, charitable foundations and volunteer-led projects. Its mission is to make nonprofit donor management and digital operations accessible without requiring several expensive commercial SaaS products. The platform should help teams understand relationships with donors and supporters, run fundraising campaigns, coordinate volunteers and events, communicate responsibly, and measure results.

This is a **real product ambition and portfolio-grade engineering project**, not an empty Java tutorial or an unvalidated claim of a secure hosted SaaS offering. Build working, testable increments suitable for future nonprofit pilot research.

## 2. Problem / opportunity
Small organizations commonly maintain contacts in spreadsheets; donations in bank exports; newsletters in separate email tools; events in forms and calendars; grant/project reports in documents. Consequences include duplicate records, fragmented histories, weak reporting, manual reconciliation, unclear consent records, loss of institutional knowledge and difficult handovers between employees and volunteers.

## 3. Product principles
- Nonprofit-first workflows, simple onboarding, low-cost self-hosting potential.
- Data privacy and least privilege before convenience.
- Accessibility, transparent behavior and open APIs.
- Clear financial semantics (pledged != received != refunded).
- Multitenancy with strict organization boundaries.
- Progressive complexity, evidence-based delivery, genuine use-case validations.

## 4. Users and permissions
- **Platform operator (SYSTEM_ADMIN)**: operates deployments, not automatically allowed to browse all donor records.
- **Organization owner (ORG_OWNER)**: manages membership, settings, exports and high-risk operations.
- **Organization admin (ORG_ADMIN)**: team management subject to policy; contacts and campaigns.
- **Fundraising manager (FUNDRAISER)**: manage donors, campaigns, donations and reports for their own organization.
- **Communication manager (COMMUNICATIONS)**: approved segments/templates and consent-aware outreach.
- **Volunteer coordinator (COORDINATOR)**: volunteers, assignments and events; restricted donation visibility.
- **Viewer (VIEWER)**: access to permitted read-only aggregated reports; never bulk-extract PII without rights.

Permissions must be enforced on the API/service layer, not hidden buttons alone. Membership can be in multiple organizations; switching tenant must not leak cached or queried data.

## 5. Prioritized scope

### P0 — secure platform foundation and CRM
**Organization / membership / access**
- Create and manage an organization; invite/disable members; assign roles; scope every request to a verified member of exactly one active tenant.
- System-issued stable UUID identities; audit membership and high-impact changes; deny by default.
- Adopt OIDC integration and environment-based secrets; document disaster recovery implications.

**Contact management**
- Contact types: PERSON and ORGANIZATION (this is distinct from tenant organization).
- Contact name and optional organization name, contactable email/phone fields with validation, status ACTIVE/ARCHIVED, created/updated timestamps and `createdBy` where meaningful.
- Relationship labels/categories: DONOR, VOLUNTEER, PARTNER, SUPPORTER (multi-category rather than one rigid enum in the target model).
- Tags and custom segmentation support without uncontrolled free-text SQL queries.
- CRUD with archive/restore policy, pagination, deterministic sorting, search, filters, duplicate detection/merge plan, clear normalization rules and concurrency handling.
- Non-contactable/anonymous supporters supported where justified. Minimize required fields: do not require an email for every offline donor.
- Prevent cross-tenant ID guessing, enumeration and leakage via error handling.

**Consent and communication preferences**
- Record channel (EMAIL, SMS etc.), purpose (newsletter/fundraising/event updates), whether consent exists, when/how/source/version it was collected and withdrawals.
- Distinguish consent from other GDPR lawful bases: do not automatically infer newsletter permission from a donation, event attendance or a CRM contact entry.
- Preserve necessary history for accountability; only send when policy allows.

### P1 — fundraising core
**Fundraising campaigns**
- Create/update/archive campaigns with title, description, optional goal, currency, start/end, status DRAFT/ACTIVE/COMPLETED/CANCELLED, owner organization.
- Require sensible transitions, organization ownership, start/end consistency and amount validations.
- Report total funds received against goals; optional restricted/unrestricted designation in future.

**Donations**
- Record manual offline donations and external-payment references. Fields: UUID, orgId, campaignId optional, donorContactId optional, amount BigDecimal positive, ISO currency, occurredAt, method, externalRef optional, status (PLEDGED/RECEIVED/REFUNDED/VOIDED or carefully justified equivalent), idempotency key for writes.
- A donation can be anonymous; donor reference is a foreign-service logical UUID only, not a cross-DB SQL foreign key.
- Protect duplicate imports/webhook retries with scoped idempotency and uniqueness policy.
- Financial reports aggregate **RECEIVED minus refunded amounts where appropriate**, not pledges. Track partial refunds explicitly if implemented; otherwise disallow them and document limitation.
- Do not actually charge cards; avoid storing PAN, CVV, bank access tokens.
- Audit changes and reversals; prefer append-only financial history and immutable event records over silent mutation.

**Reporting**
- Totals by campaign, period, payment status and donor relationship segment; currency-specific grouping (do not sum EUR and USD without FX logic).
- CSV export with authorization, safe escaping and privacy limits. Include report date range, status semantics and timezone.

### P2 — communication, volunteers and events
**Communication Service**
- Templates, segment preview, consent-aware recipient resolution, sending queue, delivery state, unsubscribes, delivery provider adapter, retries/dedupe and delivery audit. Local sandbox/mock sender first; no real outreach without permission and provider configuration.
- Outbound delivery triggered by durable messages; recipient consent must be rechecked immediately before send.

**Volunteers / events**
- Volunteer profiles tied to contacts, skills/availability, onboarding and participation; events, registration, capacity and check-in; coordinator access.
- Safeguard sensitive notes. No processing of minors' sensitive information without a separate privacy assessment and explicit requirement.

### P3 — projects, grants and impact
- Track social projects, outcomes/metrics, volunteer hours, grant applications, deadlines, reporting artifacts and funder relationships.
- Export impact reports and campaign performance; carefully define measurable impact so that financial totals are not mistaken for impact outcomes.
- Integration adapters for email providers, payment service providers, spreadsheets, accounting software and external forms.

## 6. User journeys
1. Admin creates an organization and onboards an authorized fundraising manager.
2. Fundraiser imports or manually enters fictional/demo supporters; resolves duplicates, adds categories and tags.
3. Contact opts into a newsletter with purpose + source + timestamp; later unsubscribes, preventing further campaign sends.
4. Fundraiser creates a EUR campaign with a goal and records a cash donation; dashboard reflects received funds, not mere pledges.
5. A network retry attempts to create the same donation again; idempotency prevents a duplicate record.
6. A user from organization B guesses organization A's contact UUID; API denies access without leaking personal data.
7. Viewer sees permitted aggregate campaign analytics but not unrestricted donor emails.
8. Coordinator registers volunteers for an event; later reports hours and participation.

## 7. Example domain entities (target, evolve with ADRs)
- TenantOrganization, Membership, Role/Permission.
- Contact, ContactCategory, Tag, ConsentRecord, ContactNote (restricted/audited).
- FundraisingCampaign, Donation, DonationStatusHistory, PaymentReference, IdempotencyRecord.
- EmailTemplate, CommunicationCampaign, RecipientDelivery, UnsubscribeEvent.
- VolunteerProfile, Event, Registration, Participation.
- Project, Grant, ImpactMetric, Report.

## 8. Non-functional requirements / targets
- Tested on Java 21; deterministic Maven build and meaningful CI checks.
- Docker Compose bootstraps local development with a single documented command; services have health/readiness probes.
- Documented OpenAPI 3.x, consistent error contracts, pagination capped to sane limits, input size constraints and defensible HTTP codes.
- Database migrations are repeatable, reviewed, forward-only by default; backup+restore approach written before production.
- Security: OIDC, least privilege, per-tenant authorization tests, safe logging, controlled secrets, explicit cross-service auth, dependency scans. Rate-limit abuse-prone endpoints at edge.
- Observability: request IDs, structured logs, metrics and traces without PII; service health.
- Accessibility target WCAG 2.2 AA for eventual dashboard; responsive UI and i18n architecture.
- Performance targets are initial engineering hypotheses, not verified SLAs: p95 <300ms for ordinary paginated reads under a defined local load profile; 100k contacts / org should remain queryable with appropriate indexes (benchmark and revise).
- Operational readiness requires documented deployment, alerts, restore drill, privacy notices and security review; do not claim compliance merely from implementation.

## 9. Explicitly out of initial scope
- Production payment processor, issuing legally valid German donation receipts, sending real bulk email, full accounting/ERP, automated grant eligibility decisions, AI-based donor scoring, native mobile apps, Kubernetes and customer-facing paid subscriptions. These need separate design, legal and/or business validation.

## 10. Success criteria
**Engineering v0.1:** starter imports, builds, tests, runs with PostgreSQL, documented and stable.  
**MVP alpha:** authorized multi-tenant contact workflow + campaign + donation workflow + correct aggregate report + Testcontainers / CI + audit and privacy safeguards.  
**Pilot candidate:** documented backup/recovery, secure deployment, user feedback, consent-aware communications (if enabled) and full security/privacy review.  
No milestone title by itself implies production readiness.
