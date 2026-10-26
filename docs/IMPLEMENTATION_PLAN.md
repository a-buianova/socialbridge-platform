# Next milestone — Authenticated, tenant-isolated CRM

**Status:** implementation plan; the security features below are not present in the M0 API.

The current slice establishes a PostgreSQL-backed contact workflow. Keep the next milestone focused on making that same workflow safe across organizations. Defer Fundraising, communication delivery, a gateway and a frontend until the security boundary is demonstrated.

## Sequence

| Step | Deliverable | Evidence required before completion |
| --- | --- | --- |
| 1. Identity and capability contract | Review [ADR-0003](adr/0003-tenant-identity-contract.md), organization selection, membership lifecycle, endpoint capability matrix and `401`/`403`/`404` rules | Threat cases and integration-test matrix reviewed; no trusted caller-controlled tenant shortcut |
| 2. Organization-owned persistence | Flyway organization/membership tables, explicit prototype-data ownership, `organization_id` on contacts and scoped queries/indexes | PostgreSQL migration tests; same email allowed across organizations; all reads/counts/writes scoped |
| 3. Authentication and authorization | Local OIDC provider setup, resource-server validation and verified membership/role enforcement in services | Invalid-token and revoked/disabled-member failures; cross-organization UUID guessing denied for every route |
| 4. Audit and consent foundation | Actor/scope-aware mutation audit, minimal purpose/channel consent and withdrawal history design | No PII in operational logs; creation is never interpreted as consent; withdrawal preserves history |
| 5. Secured CRM contract and runbook | Local bootstrap, OpenAPI security scheme, updated examples and operator boundaries | Repeatable Java/Docker setup; PostgreSQL tests and CI; no mock-auth runtime fallback |

These steps can be reviewed incrementally, but the API must remain isolated while any route is unauthenticated or incompletely scoped. Keep schema and code changes coherent rather than briefly exposing an unscoped migration state.

## Acceptance gate

1. An unauthenticated caller cannot read or change contacts; only deliberately public probes/docs remain public by policy.
2. An organization A member cannot read, list, count, update or archive organization B's contacts, even with a known UUID. Error bodies disclose no foreign contact fields.
3. A forged organization selection, disabled membership, revoked capability or inappropriate role is denied. Switching organizations cannot reuse cached authorization from another scope.
4. Organization-scoped uniqueness is enforced by PostgreSQL under concurrent writes. Optional email remains supported. Existing global uniqueness is removed only after a reviewed data-ownership migration.
5. Every mutation records the authenticated actor and organization. Audit and consent data have documented access/retention decisions; sensitive field values are not placed in ordinary logs.
6. `mvn -B verify`, PostgreSQL/Testcontainers checks and the documented local OIDC/Compose workflow pass. CI results are inspected rather than inferred from a local build.

## Follow-up after the security gate

Evolve the single relationship `type` into a person/organization kind plus multiple categories/tags; specify search, restore/merge and stale-client update preconditions. Complete consent and privacy workflows before outreach or real records. The first subsequent business service is Fundraising, with its own database, exact money/currency/status semantics, scoped idempotency and audited changes. No new empty service skeleton is needed for this plan.

Production use remains a separate decision requiring a security/privacy review, supported dependency review, access-controlled backups and restore evidence, retention/export/deletion procedures and operational ownership.
