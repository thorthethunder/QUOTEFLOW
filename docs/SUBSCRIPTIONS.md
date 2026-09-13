# QuoteFlow Platform Subscriptions & Entitlements

**IMPLEMENTED (Phase 11 + 12)** — SaaS plan model, usage limits, feature gates, and Razorpay platform billing.

Platform subscription domain is **separate** from tenant Invoice/Payment/Receipt money flows (ADR-004, ADR-017).

See [BILLING.md](BILLING.md) for checkout, webhooks, and operator setup.

## Plans (code catalog)

| Plan | Customers (active) | Quotations / month | Invoices / month | Remove branding | Multi-user |
|------|--------------------|--------------------|------------------|-----------------|------------|
| FREE | 5 | 5 | 5 | No | No |
| PRO | Unlimited* | Unlimited* | Unlimited* | Yes | No |
| BUSINESS | Unlimited* | Unlimited* | Unlimited* | Yes | Yes (entitlement only; invites FUTURE) |

\*Commercial unlimited still subject to technical abuse caps (line items, page size, rate limits).

Display pricing (catalog authority): Free ₹0 · Pro ₹199/mo or ₹1,999/yr · Business ₹499/mo. Razorpay Plans must match; Angular never supplies amounts.

## Schema

Table `subscriptions` (V8 + V9): one row per `business_id` (UNIQUE). Fields include `plan`, `status`, `billing_interval`, provider IDs/status, period timestamps, `cancel_at_period_end`, `cancelled_at`, `last_provider_event_at`.

Default backfill: every existing Business → `FREE` / `ACTIVE`.

Registration creates a FREE subscription in the same flow as Business + OWNER.

## Entitlement architecture

- `PlanCatalog` / `PlanDefinition` — code-defined capabilities (`null` limit = unlimited)
- `SubscriptionService` — load entitlements, catalog, ensure default, test-only plan force
- `UsageService` — counts from source tables (customers ACTIVE; quotations/invoices by `created_at`)
- `EntitlementService` — `lockAndRequireQuota` under tenant subscription row lock

Controllers never trust Angular. Domain services call entitlement before create.

## Monthly period semantics (Phase 11)

Calendar month in **Business timezone** (e.g. Asia/Kolkata).

Half-open Instant range:

`created_at >= localMonthStartInstant AND created_at < nextMonthStartInstant`

Quota uses **creation time**, not `issue_date` (backdating cannot bypass).

Phase 12 may align quotas to Razorpay billing periods — do not treat calendar month as provider period.

## What counts

| Resource | Counts toward | Notes |
|----------|---------------|--------|
| ACTIVE customer | Customer limit | ARCHIVED frees capacity |
| Quotation create | Monthly quotation quota | CANCELLED still counts |
| Invoice create (standalone or convert) | Monthly invoice quota | CANCELLED still counts |

## Concurrency

`SELECT … FOR UPDATE` on the tenant `subscriptions` row, then count + create in **one transaction**. Serializes plan-sensitive creates per tenant. Expected contention is per-tenant only.

## Downgrade / data access

PRO→FREE: existing data remains readable/editable/archivable; new creates blocked when over FREE limits. Payments and receipt PDFs always allowed for existing invoices. Dashboard reporting is not plan-gated.

## PDF branding

`show_quoteflow_branding` on quotations (frozen at SEND) and payments (frozen at record). DRAFT PDFs may follow live entitlement. Regenerated SENT PDFs respect snapshot.

## APIs

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/v1/subscription` | plan, limits/usage, features, billing metadata |
| GET | `/api/v1/plans` | authenticated catalog (+ safe billing availability) |
| POST | `/api/v1/billing/checkout` | OWNER — create/reuse provider subscription |
| POST | `/api/v1/billing/checkout/verify` | OWNER — checkout HMAC verify + reconcile |
| POST | `/api/v1/billing/subscription/cancel` | OWNER — cancel at period end |
| POST | `/api/v1/webhooks/razorpay` | public; signature auth |

No tenant-writable plan/status/provider fields from arbitrary clients. Paid-to-paid changes deferred.

Errors: `PLAN_LIMIT_REACHED` (403); billing: `BILLING_NOT_AVAILABLE`, `BILLING_CONFIGURATION_ERROR`, `INVALID_PLAN_TRANSITION`, `BILLING_CHECKOUT_FAILED`, `BILLING_ALREADY_ACTIVE`, `BILLING_VERIFICATION_FAILED`, `BILLING_PROVIDER_UNAVAILABLE`.

## Angular

- `/app/plan` — Plan & usage with interval selector + Razorpay Checkout (lazy script)
- `EntitlementStore` — UX gates only; refresh after verify / webhook lag poll
- When `billingCheckoutAvailable=false`: “Upgrade unavailable”
- Public landing pricing — register/login then Plan page (no anonymous checkout)

See [ADR-016](adr/ADR-016-saas-entitlement-and-usage-limit-strategy.md), [ADR-017](adr/ADR-017-platform-billing-and-razorpay-subscriptions.md), [BILLING.md](BILLING.md).
