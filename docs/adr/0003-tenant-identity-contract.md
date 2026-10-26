# ADR-0003 — OIDC identity and verified organization scope

**Status:** Proposed for the next milestone; not implemented

**Decision boundary:** Authentication and tenant isolation before real records or Fundraising integration.

## Context

The CRM currently trusts every caller and contains no organization ownership. A caller-supplied organization or user header cannot establish identity or membership. A future gateway can validate a token, but each domain service must still enforce its own authorization and data boundary. A tenant organization is distinct from an organizational contact recorded in the CRM.

## Proposed decision

1. Use Spring Security's OAuth2 resource server with a standards-based OIDC provider. Use a maintained Keycloak release for local development; do not implement passwords or JWT cryptography. Validate signature, issuer, audience, expiry and accepted algorithms. A token's subject identifies the user; it does not authorize arbitrary organization IDs.
2. Start with an identity/membership module and explicit authorization boundary in the existing deployment. Add a separate identity application only when its independent ownership or operational needs justify another service. Keep provider authentication separate from application membership policy.
3. Resolve a selected organization to an active, server-verified membership for the authenticated subject on every request. If selection is supplied in a path/header, treat it as a request to authorize, not a trusted claim. Deny absent, disabled or ambiguous membership; membership revocation must have a documented bounded effect. Define cache invalidation before caching positive authorization decisions.
4. Store `organization_id` on every tenant-owned record and scope all repository reads and writes by it, including list/count/existence, update/archive and future export/background jobs. Avoid unscoped `findById` in tenant-facing services. Database constraints and indexes must include organization scope; allow the same email in different organizations. A compound reference or equivalent verified service logic must prevent cross-organization relationships.
5. Enforce capabilities in services, not only controllers or the future gateway. Set a default-deny policy. Return `401` for missing/invalid authentication, `403` for a known membership lacking an operation's capability, and `404` for missing or undisclosable resource UUIDs. Do not reveal whether another organization's contact exists.
6. Audit identity, membership and contact mutations with actor, organization, action, object ID, timestamp and trace ID. Keep contact fields, tokens and consent text out of operational logs. Define retention and access policy for the audit store.

## Initial capability proposal

This table is a design input for M1 tests, not currently enforced access control.

| Role | Proposed capabilities within its verified organization |
| --- | --- |
| `ORG_OWNER` | Organization settings, membership and contact management; separately authorized exports/high-risk actions |
| `ORG_ADMIN` | Delegated membership and contact management; cannot silently become owner |
| `FUNDRAISER` | Read/create/update/archive contacts and later fundraising records; no membership administration |
| `COMMUNICATIONS` | Purpose-limited contact/consent access and later approved outreach; no unrestricted financial data |
| `COORDINATOR` | Relevant volunteer/contact access; no donor financial records or unrestricted exports |
| `VIEWER` | Explicit permitted aggregate reports; no implicit bulk contact-PII access |
| `SYSTEM_ADMIN` | Platform operations only; no automatic cross-organization PII access |

M1 should implement only the capabilities its endpoints need. Unimplemented role operations remain denied. Owner transfer, invitations and emergency operator access require explicit workflows and audit records.

## Migration and acceptance

- Add organization and membership tables, then backfill only explicitly identified fictional prototype data through a controlled procedure. Production migration of unknown ownership is blocked pending a real ownership mapping.
- Add tenant columns, constraints and indexes; update every query and change the global email index only after ownership is verified. Preserve forward-only Flyway history.
- Introduce token validation and membership-aware authorization together; do not expose a partially scoped API. Local mock identity is permitted only in isolated tests, never as a runtime fallback.
- Test missing/expired/wrong-issuer/wrong-audience tokens, disabled membership, insufficient roles and every cross-tenant read/list/count/update/archive path using guessed UUIDs. Include same-email cross-organization success and within-organization conflict.
- Test role/membership revocation, tenant switching, background tasks and audit actor/scope. Document credential bootstrap and rotation without committed secrets.

## Consequences

An external provider handles authentication while the application owns NGO membership and capabilities. This avoids a custom security protocol and keeps domain authorization visible. It introduces provider configuration and failure modes that must be covered by local setup and integration tests. It does not by itself deliver consent, retention, deletion/export workflows, backups or a production security review.
