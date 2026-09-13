# QuoteFlow Database

## Status

| Area | State |
|------|--------|
| Phase 2 identity/tenant schema | **IMPLEMENTED** |
| Phase 3 email canonical CHECK (V2) | **IMPLEMENTED** |
| Customer / quotation / invoice / payment tables | Customers **IMPLEMENTED** (V3); quotations **IMPLEMENTED** (V4–V5); invoices **IMPLEMENTED** (V6); payments **IMPLEMENTED** (V7) |
| Platform subscriptions | **IMPLEMENTED** (V8) — one row per business; FREE backfill |
| Platform billing hardening | **IMPLEMENTED** (V9) — provider fields, `billing_webhook_events`, `billing_transactions` |
| Notifications outbox | **IMPLEMENTED** (V10) — tenant-scoped email outbox |
| Reporting summary tables | **Not required** (Phase 10 aggregates over domain tables) |
| Quotation/invoice `(business_id, created_at)` indexes | **IMPLEMENTED** (V8) for monthly quota counts |
| Full `audit_logs` event table | **FUTURE** (entity timestamps exist now) |

## UUID policy

- PostgreSQL column type: `UUID`
- Java type: `java.util.UUID`
- Generation: **application-generated** via `UUID.randomUUID()` in entity constructors
- No extra UUID libraries

## Timestamp policy

- PostgreSQL: `TIMESTAMPTZ`
- Java: `java.time.Instant` (UTC semantics)
- Populated via JPA `@PrePersist` / `@PreUpdate` on `BaseAuditableEntity` (and `RefreshToken.createdAt`)

## Tables (Phase 2)

### `businesses`

Tenant root. Required: `id`, `name`, `currency` (ISO 4217, 3 letters), `timezone` (IANA), `status`, `created_at`, `updated_at`.  
Optional contact/address/tax fields are nullable.  
Status CHECK: `ACTIVE` | `SUSPENDED` | `CLOSED`.  
Business **name is not** globally unique.

### `app_users`

Login identity for a single business. Required: `id`, `business_id`, `email`, `password_hash`, `tenant_role`, `status`, `email_verified`, timestamps.  
No `password` column — only `password_hash` (length 255 for BCrypt and future algorithms). Hashing occurs in **Phase 3**.

### `refresh_tokens`

Phase 3 will issue opaque client tokens and persist **only** `token_hash`.  
Columns: `id`, `user_id`, `token_hash`, `expires_at`, `revoked_at`, `created_at`, `last_used_at`.  
No raw `token` column.

### `flyway_schema_history`

Flyway metadata.

## Relationships

| FK | Behavior |
|----|----------|
| `app_users.business_id` → `businesses.id` | `ON DELETE RESTRICT` — cannot delete business while users exist |
| `refresh_tokens.user_id` → `app_users.id` | `ON DELETE CASCADE` — tokens removed with user |
| `customers.business_id` → `businesses.id` | `ON DELETE RESTRICT` — cannot delete business while customers exist |
| `quotations.business_id` → `businesses.id` | `ON DELETE RESTRICT` |
| `quotations.customer_id` → `customers.id` | `ON DELETE RESTRICT` — archive customer; keep historical quotes |
| `quotation_items.quotation_id` → `quotations.id` | `ON DELETE CASCADE` — items owned by quotation |
| `document_sequences.business_id` → `businesses.id` | `ON DELETE RESTRICT` |

Ownership: Business **1 → many** Users / Customers / Quotations.

**Owner invariant:** every active business should eventually have ≥1 `OWNER`. Enforced in Phase 3+ services, not DB triggers.

## Tables (Phase 5)

### `customers`

Tenant-owned contacts. Required: `id`, `business_id`, `display_name`, `status`, timestamps.  
Optional: email, phone, company_name, address fields, country_code (ISO alpha-2), tax_id, notes (max 2000).  
Status CHECK: `ACTIVE` | `ARCHIVED`. Email is **not** unique (global or per-tenant).  
Indexes: `idx_customers_business_id`; `idx_customers_business_status`.

## Tables (Phase 6)

### `document_sequences`

Tenant document counters: PK `(business_id, document_type)`, `next_value`, `updated_at`. Type CHECK currently `QUOTATION` only. Allocated with row lock — see [QUOTATIONS.md](QUOTATIONS.md) / ADR-010.

### `quotations`

Tenant financial drafts/sent docs. Money columns `NUMERIC(19,4)`. Status: `DRAFT` | `SENT` | `CANCELLED`. Unique `(business_id, quotation_number)`. Customer snapshot columns + `customer_id` FK. Optimistic `version`. Indexes: business, business+status, customer, business+issue_date.

### `quotation_items`

Owned lines: `position`, description, quantity, unit_price, line_subtotal. CASCADE delete with quotation.

## Tables (Phase 7)

### Business snapshot columns on `quotations`

Added in V5: `business_name` (NOT NULL after backfill), contact/address/tax snapshot fields matching Business profile. Existing rows backfilled from current `businesses` (pre-production baseline).

## Tables (Phase 8)

### `invoices` / `invoice_items`

Historical invoice documents with customer + business snapshots, authoritative money columns, optional `source_quotation_id` (UNIQUE when non-null), statuses `DRAFT|SENT|CANCELLED`, optimistic `version`.

FK: business/customer/quotation → **RESTRICT**; invoice_items → invoice **CASCADE**.

Indexes: `business_id`, `(business_id, status)`, `(business_id, invoice_number)` unique, `customer_id`, `(business_id, issue_date)`, `invoice_items(invoice_id)`.

`document_sequences.document_type` allows `QUOTATION` | `INVOICE`.

## Tables (Phase 9)

### `payments`

Manual payment records with receipt number, method, frozen historical balance fields, `RECORDED`/`VOIDED` status, optional void audit. FK business/invoice **RESTRICT**. Unique `(business_id, receipt_number)`.

## Tables (Phase 12 — platform billing only)

### `subscriptions` (V9 extensions)

Adds `billing_interval`, `provider_plan_id`, `provider_status`, `last_provider_event_at`, `cancelled_at`. FREE stays `provider` null/`NONE` without Razorpay.

### `billing_webhook_events`

Durable inbox: provider, provider_event_id (unique), event_type, payload_hash, processing_status, timestamps. Not tenant `payments`.

### `billing_transactions`

Optional SaaS charge reconciliation (provider payment/subscription ids, plan, amount, currency). Never mixed with tenant invoice collections.

`document_sequences.document_type` allows `QUOTATION` | `INVOICE` | `RECEIPT`.

See [PAYMENTS.md](PAYMENTS.md) / ADR-014.

## Email policy

- Normalize: trim + lowercase (`EmailNormalizer` + entity callbacks); `Locale.ROOT`
- Uniqueness: **GLOBAL** unique on `app_users.email`
- DB CHECK (V2): `email = lower(btrim(email))`
- Documented in ADR-006

## Roles

Tenant roles on `app_users.tenant_role`: `OWNER` | `ADMIN` | `STAFF` (CHECK constrained).  
These are **not** platform operator roles (`PLATFORM_*`). See ADR-005 / ADR-006 / `ADMIN_ARCHITECTURE.md`.

## User status

`ACTIVE` | `DISABLED` (CHECK). `email_verified` default false. `last_login_at` updated on successful login (Phase 3).

## Refresh token cleanup (FUTURE)

Expired/revoked rows will accumulate. A simple scheduled DELETE by `expires_at` / `revoked_at` is sufficient later — no Kafka/worker required for MVP.

## Indexes

| Index | Rationale |
|-------|-----------|
| PK on all tables | Identity |
| `uq_app_users_email` | Hot auth lookup path |
| `idx_app_users_business_id` | Tenant membership listing |
| `uq_refresh_tokens_token_hash` | Token lookup + uniqueness |
| `idx_refresh_tokens_user_id` | Revoke/list by user |
| `idx_refresh_tokens_expires_at` | Expiry cleanup jobs (Phase 3+) |
| `idx_customers_business_id` | Tenant customer list |
| `idx_customers_business_status` | Active/archived tenant lists |
| `uq_quotations_business_number` | Tenant quotation number uniqueness |
| `idx_quotations_business_id` | Tenant quotation list |
| `idx_quotations_business_status` | Status-filtered lists |
| `idx_quotations_customer_id` | Quotations by customer |
| `idx_quotations_business_issue_date` | Date-sorted lists |
| `idx_quotation_items_quotation_id` | Detail item load |

No speculative composite indexes for nonexistent query patterns.

## Tenant-aware access convention

Trusted tenant context comes from authenticated identity (Phase 3):

```text
Verified JWT → AuthenticatedUser → userId / businessId → tenant-aware queries
```

Do **not** treat client-supplied `businessId` as authorization. Prefer explicit tenant-scoped repository methods for tenant-owned resources. Identity bootstrap lookups (`findByEmail`) are an intentional exception.

## Migration policy

- Flyway owns schema; Hibernate `ddl-auto=validate` (production: validate only — never create/update)
- Default: Flyway **enabled** (`FLYWAY_ENABLED` default `true`)
- Order: Flyway migrate → Hibernate validate
- Phase 14: **no V11** — V1–V10 remain immutable; no schema hardening required this phase
- Phase 15: **no V11** — notification stale-SENDING recovery uses existing `updated_at` lease (no schema change)
- Released migrations are **immutable**; fix forward with V2+
- Community Flyway rolls forward (no automatic rollback); back up before high-risk prod migrations

## Soft delete

Customer lifecycle uses `ACTIVE` / `ARCHIVED` (no hard delete). Prefer explicit `status` fields for other entities. Account deletion workflows come later.

## Privacy (PII)

Tables hold business/user email, phone, addresses, tax IDs. Do not log full values casually; never put PII in metrics labels or Actuator dumps.
