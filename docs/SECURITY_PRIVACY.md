# Security, privacy and EU nonprofit data handling

**This is engineering guidance, not a legal determination or proof of GDPR compliance. Obtain legal/privacy review before using real donor or volunteer data.**

## Current state / blocker
The current starter CRM has unauthenticated endpoints and no tenant isolation. It is suitable **only for synthetic data on a local, isolated development machine**. Do not expose it publicly or seed real contacts. The initial milestone MUST keep this limitation visible in README and operational configuration.

## Authentication and authorization
- Use OIDC with a maintained provider (Keycloak locally is acceptable), Spring Security OAuth2 resource server. Do not hand-roll JWTs, password hashing or OAuth flows.
- Define roles and per-resource capabilities, default-deny policies; enforce at controller/service and every tenant-owned query.
- Organization ID must be validated against authenticated membership and all service requests. When multiple orgs are possible, validate active org context; never trust arbitrary caller-supplied org IDs.
- Protect service-to-service traffic using appropriate credential flow/scopes and least privilege; plan for token/key rotation.
- Test unauthorized, insufficient-role, expired/invalid token, cross-tenant read/write/delete, and ID enumeration scenarios.

## Data minimization and consent
- Contact creation is not consent to fundraising newsletters or marketing emails.
- Consent capture includes purpose, channel, choice, policy text/version, source, timestamp; explicit opt-out/withdrawal with immutable history as appropriate.
- Distinguish contract, legitimate interests and consent (where applicable) through legal review; don't make blanket claims of consent requirements in all circumstances.
- Build deletion/export/rectification workflows; reconcile erasure requests with statutory retention requirements for donation bookkeeping.
- Do not retain full card numbers, sensitive identity documents, children’s sensitive information, unnecessary free-text special-category data.
- Restrict bulk export, contact notes, direct identifiers and logs. Audit access to sensitive data; set retention and deletion windows by purpose.

## Application security
- Bean Validation plus database constraints, output encoding for exports/frontend, safe error messages, parameterized queries, input size caps.
- Rate limiting and abuse protection for public/auth endpoints; CSRF for browser sessions if used; restrictive CORS and cookie controls.
- No hardcoded secrets or production default passwords. Use separate environments and secrets stores; sanitize stacktraces/errors.
- Keep JDK/dependencies and container bases patched; automate vulnerability scans, audit dependencies and prepare a security disclosure policy.
- Limit database users to required schema/operations; encrypt data in transit, secure at rest where deployment requires; document backups, access controls and restore tests.
- Record audit events for contacts, org roles, donations, export and consent. Make audit entries tamper-evident/retained according to policy where possible.

## Communication rules
- Suppress sends when consent/preferences are missing or withdrawn for the relevant purpose, and recheck immediately before delivery.
- Unsubscribe must be effective promptly and across retried jobs. Store only necessary sending metadata in logs.
- Real outbound mail requires compliant provider setup and review of regional electronic communications requirements.

## Deployment readiness gate
Do not label any release production-ready until the following are evidenced: auth and per-tenant access tests; reproducible CI; supported versions and scan results; monitored backups and tested restores; secrets and configuration review; threat model; privacy/document retention assessment; operational/runbook and incident-response ownership.
