# Proposed API contracts (draft; implementation must document deviations)

These paths describe the **desired** `/api/v1` design; they are not all implemented. Never claim missing endpoints exist.

## CRM service
```http
POST   /api/v1/contacts
GET    /api/v1/contacts?page=0&size=20&category=DONOR&search=example
GET    /api/v1/contacts/{id}
PATCH  /api/v1/contacts/{id}
POST   /api/v1/contacts/{id}/archive
POST   /api/v1/contacts/{id}/restore
GET    /api/v1/contacts/{id}/consents
POST   /api/v1/contacts/{id}/consents
POST   /api/v1/contacts/{id}/consents/{consentId}/withdraw
GET    /api/v1/tags
POST   /api/v1/tags
```
The current starter instead exposes `PUT` and `DELETE` (soft archive) on contacts and a single contact type. Treat moving to the target API as a planned versioned change; keep backward compatibility within an established API version or document a breaking alpha change.

Example contact create (synthetic data only):
```json
{
  "kind": "PERSON",
  "displayName": "Ada Example",
  "email": "ada@example.org",
  "categories": ["DONOR", "SUPPORTER"],
  "tags": ["local-chapter"]
}
```
Email is optional for offline/non-contactable individuals; identity/organization scope comes from authenticated context rather than unvalidated request fields.

## Fundraising service
```http
POST   /api/v1/campaigns
GET    /api/v1/campaigns?page=0&size=20&status=ACTIVE
GET    /api/v1/campaigns/{id}
PATCH  /api/v1/campaigns/{id}
POST   /api/v1/donations                    # require Idempotency-Key
GET    /api/v1/donations/{id}
GET    /api/v1/donations?campaignId={id}&status=RECEIVED
POST   /api/v1/donations/{id}/refund         # future if partial/full refund semantics defined
GET    /api/v1/reports/fundraising?from=...&to=...
```
Example donation record (not a payment charge):
```http
POST /api/v1/donations
Idempotency-Key: demo-e19aa073-6a1c-4ef6-8f32-b2104dbc315a
Content-Type: application/json

{
  "donorContactId": "6e8f48c9-0f91-49d1-bf36-89ae21be85c7",
  "campaignId": "d49b2d2b-ae45-4b76-bf62-c419c2a8be9e",
  "amount": "25.00",
  "currency": "EUR",
  "status": "RECEIVED",
  "method": "BANK_TRANSFER",
  "occurredAt": "2026-10-26T10:00:00Z"
}
```
Use amount as a decimal string for JSON precision in integrations, map to BigDecimal; actual wire format should be consistent and tested. Donor/campaign UUIDs must be authorized within the active organization. Prevent duplicate writes under concurrent requests.

## Identity / membership (planned)
```http
GET    /api/v1/me
GET    /api/v1/organizations
POST   /api/v1/organizations
GET    /api/v1/organizations/{id}/members
POST   /api/v1/organizations/{id}/invitations
PATCH  /api/v1/organizations/{id}/members/{memberId}
```

## Standard error contract
Prefer `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`, `traceId` and field validation errors (without leaking credentials or personal details). Status rules: 400 malformed input; 401 unauthenticated; 403 forbidden; 404 missing or undisclosable resource; 409 conflict/idempotency mismatch; 422 for domain state rule if consistently applied; 429 throttled; 500 for unexpected server error.

## API testing matrix
Valid and invalid requests, missing roles, cross-org IDs, case folding, absent emails, duplicate conflicts (including concurrency), pagination boundaries, money precision, conflicting currency, campaign transitions, retry idempotency and validation response format.

## Current CRM contract

The preceding sections are the target design. This section records the implemented M0 contract and takes precedence when running the current application. **The current API is unauthenticated and has no tenant isolation; use synthetic data through loopback only.** No fundraising, membership, consent, tags, restore or search endpoints are implemented.

### Routes

| Method | Path | Success | Behavior |
| --- | --- | --- | --- |
| `POST` | `/api/v1/contacts` | `201` | Create; `Location` points to the created contact |
| `GET` | `/api/v1/contacts` | `200` | Active contacts; optional `type`, `page`, `size` |
| `GET` | `/api/v1/contacts/{id}` | `200` | Active contact; archived/missing returns `404` |
| `PUT` | `/api/v1/contacts/{id}` | `200` | Replace all editable fields; archived/missing returns `404` |
| `DELETE` | `/api/v1/contacts/{id}` | `204` | Soft archive; repeat for existing archived row is `204`; unknown UUID is `404` |
| `GET` | `/actuator/health` | `200` when healthy | Overall health |
| `GET` | `/actuator/health/readiness` | `200` when ready | Application and database readiness |
| `GET` | `/v3/api-docs` | `200` | Generated OpenAPI document |

The local Swagger UI is at `/swagger-ui/index.html`. Generated route schemas supplement this contract; they do not imply that the proposed APIs above exist.

### Contact payloads and normalization

Create and replace accept the same fields:

```json
{"name":"Ada Example","email":"ADA@example.org","type":"DONOR"}
```

- `name`: required, nonblank, at most 120 characters after trimming surrounding whitespace.
- `email`: optional; absent, `null` or blank becomes `null`. Supplied values are trimmed, lowercased with locale-independent rules and validated; maximum 254 characters after normalization.
- `type`: required; one of `DONOR`, `VOLUNTEER`, `PARTNER`, `SUPPORTER`. This is one relationship category, not the target `PERSON`/`ORGANIZATION` kind.

`PUT` replaces these fields; omitting `email` therefore clears it. Current responses contain `id` (UUID), `name`, normalized `email` or `null`, `type`, `createdAt` (UTC timestamp), and `archived` (boolean). There is no tenant, consent or client-supplied version field.

Every non-null email is reserved across both active and archived records by PostgreSQL's unique `LOWER(email)` index. Duplicate creation or replacement returns `409`, including races that pass an earlier existence precheck. Multiple contacts without email are allowed. This global prototype policy must become organization-scoped before multi-tenant use; see [ADR-0002](adr/0002-contact-consistency.md).

### Pagination and concurrency

`page` defaults to `0` and must be nonnegative. `size` defaults to `20` and must be from `1` through `100`. The offset `page * size` must not exceed `2147483647`, the JPA offset limit. Invalid numbers, excessive offsets, enum values and UUIDs return `400`. The fixed ordering is `createdAt DESC, id DESC`; clients cannot choose arbitrary property names to sort by.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

The unique tie breaker provides deterministic ordering for a fixed dataset. Concurrent inserts can still shift offset pages; this is not a snapshot cursor. Internal optimistic row versions prevent overlapping transactions from silently overwriting an update or undoing archival; a collision returns `409`. No `ETag`/`If-Match` precondition currently prevents a later request based on stale client data.

**Early alpha response changes:** the explicit page replaces Spring Data's serialized implementation details while preserving `content` and `totalElements`. Error bodies now follow Problem Details rather than the starter's `{timestamp,status,message}` map. Existing HTTP routes and the contact response fields are retained. Clients depending on removed framework page properties or the old error map must adapt.

### Errors and request correlation

Errors have content type `application/problem+json` and fields `title`, `status`, `detail`, `instance` and `traceId`. The optional `type` defaults to `about:blank` under RFC 9457; the current serializer omits that default value. `instance` is the request path without its query string. Details do not contain rejected contact values, SQL or stack traces.

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/contacts",
  "traceId": "cf9d8cf2-6874-437f-ad16-fce211fe6f63",
  "errors": [{"field":"name","message":"must not be blank"}]
}
```

Validation failures add `errors`, sorted by field and message, with only `field` and `message`. Other errors do not require that extension. Malformed JSON, invalid enum/UUID/query types and invalid pagination are `400`; unavailable contacts are `404`; duplicates or optimistic write collisions are `409`; unexpected faults are `500` with a generic detail. Unsupported methods and media types retain their appropriate HTTP status.

Clients may send `X-Request-ID` as a UUID in the standard `8-4-4-4-12` hexadecimal format. It is canonicalized to lowercase; missing or invalid values are replaced with a generated UUID. The response echoes the identifier in `X-Request-ID`, and error `traceId` uses the same value. Correlation does not authenticate the caller.

The `401`/`403` authorization behavior in the target contract is planned for [the next security milestone](IMPLEMENTATION_PLAN.md); the current application does not enforce it.
