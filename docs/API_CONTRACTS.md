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
