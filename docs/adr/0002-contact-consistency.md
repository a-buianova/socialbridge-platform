# ADR-0002 — Contact identity, archival and concurrent writes

**Status:** Accepted for the local M0 slice

**Scope:** Current unauthenticated, single-dataset prototype; revisit before tenant rollout.

## Context

The starter's PostgreSQL migration already reserves `LOWER(email)` across active and archived rows, while its tests use H2 with Flyway disabled. Its email is mandatory, although an offline donor may have no email. Concurrent update/archive transactions can overwrite each other without a row version. Ordering only by creation time is ambiguous when timestamps tie.

## Decision

- Normalize names with surrounding whitespace removed. Normalize supplied email with trimming and `Locale.ROOT` lowercase before validation; absent or blank email becomes `null`.
- Allow multiple contacts without email. Retain the existing PostgreSQL unique index on `LOWER(email)` for non-null values, including archived records. Archive hides a record; it does not erase identity or release its email.
- Treat the database unique index as the final authority under concurrency. An existence precheck improves ordinary conflict handling but cannot make a write race-safe. Translate only the known email constraint into a duplicate-email conflict; unrelated integrity failures must not masquerade as duplicates.
- Preserve `POST`, `GET`, `PUT` and soft-archive `DELETE` routes. `GET`/`PUT` of archived contacts return `404`; `DELETE` of an existing archived contact returns `204`, and an unknown UUID returns `404`.
- Add a JPA optimistic version with a forward migration. Competing transactions cannot silently undo archival; a conflicting transaction returns `409`. This is transaction-level protection, not stale-client preconditions: `ETag`/`If-Match` remains a separate API decision.
- Return an explicit page DTO, ordered by `createdAt DESC, id DESC`. A unique tie breaker makes ordering deterministic for an unchanged dataset; offset pages do not provide a snapshot across concurrent inserts.
- Verify migrations, null handling, the case-insensitive index, conflicts and optimistic locking on PostgreSQL with Testcontainers. H2 compatibility mode cannot establish these guarantees.

## Consequences and alternatives

Reserving archived emails preserves the starter's identity policy and reduces accidental duplicate histories. A legitimate second person sharing an email still requires an explicit merge/shared-address policy; the current prototype does not resolve that case. A partial active-only index would allow reuse after archive but could associate future records with a different historical person, so it is deferred.

Optional email supports offline contacts without inventing placeholder addresses. The remaining single `type` is a relationship category, not a person/organization distinction; migrating to kind plus multiple categories belongs to the secured CRM model.

The global email index is temporary. Tenant rollout must replace it with an organization-scoped non-null uniqueness policy through a reviewed migration, so the same email is allowed in different organizations. Never assign ambiguous pre-existing records to an arbitrary tenant automatically. See [ADR-0003](0003-tenant-identity-contract.md).
