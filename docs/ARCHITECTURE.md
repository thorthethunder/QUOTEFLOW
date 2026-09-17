# QuoteFlow Architecture

## Status

| Area | State |
|------|--------|
| Phase 1 foundation | **IMPLEMENTED** |
| Modular monolith (single deployable) | **IMPLEMENTED** (shell) |
| Business / User / refresh_tokens schema | **IMPLEMENTED** (Phase 2) |
| Authentication / JWT / trusted tenant context | **IMPLEMENTED** (Phase 3) |
| Angular auth + app shell | **IMPLEMENTED** (Phase 4) |
| Customer management | **IMPLEMENTED** (Phase 5) |
| Quotation management + calculation | **IMPLEMENTED** (Phase 6) |
| Quotation PDF + business snapshot | **IMPLEMENTED** (Phase 7); invoice PDF **FUTURE** |
| Invoice domain + quote conversion | **IMPLEMENTED** (Phase 8) |
| Manual payments + receipt PDF | **IMPLEMENTED** (Phase 9); gateways **FUTURE** |
| Tenant dashboard / reporting | **IMPLEMENTED** (Phase 10); platform admin **FUTURE** |
| Platform SaaS plans / entitlements | **IMPLEMENTED** (Phase 11) |
| Platform billing (Razorpay subscriptions) | **IMPLEMENTED** (Phase 12) — see [BILLING.md](BILLING.md), [ADR-017](adr/ADR-017-platform-billing-and-razorpay-subscriptions.md) |
| Transactional email / notifications | **IMPLEMENTED** (Phase 13) — see [NOTIFICATIONS.md](NOTIFICATIONS.md), [EMAIL.md](EMAIL.md), [ADR-018](adr/ADR-018-transactional-email-and-notification-outbox.md) |
| Security hardening / production assurance | **IMPLEMENTED** (Phase 14) — see [SECURITY_TEST_MATRIX.md](SECURITY_TEST_MATRIX.md), [ADR-019](adr/ADR-019-production-security-and-assurance-baseline.md) |
| Docker / production deployment packaging | **IMPLEMENTED** (Phase 15) — containers + config |
| Hosted staging (Railway + Cloudflare Pages) | **CLOSED PASS** (Phase 16) — see [STAGING_VALIDATION.md](STAGING_VALIDATION.md) |
| Production readiness / release gates | **CONDITIONAL PASS** (Phase 17) — [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md); launch **NOT READY** |
| Platform admin / FinOps dashboards | **FUTURE** — documented only |
| Platform billing vs tenant payments | Separation **enforced** ([ADR-004](adr/ADR-004-platform-billing-vs-tenant-payments.md)); SaaS charges never in `payments` |
| Grafana / Prometheus stack | **FUTURE** — Actuator only for now |
| Redis / Kafka / microservices | **FUTURE** — do not deploy prematurely |
| AI providers (Ollama + Spring AI) | **AI Phase 3** — Quote Assistant + read-only Business Copilot — [BUSINESS_COPILOT.md](BUSINESS_COPILOT.md) |

**Principle:** Design for scale. Deploy for current scale.

## IMPLEMENTED — Stage 1 runtime

```text
Internet / Local
       ↓
   Angular (landing · login/register · auth shell)
       ↓  REST/JSON (+ HttpOnly refresh cookie, same-site)
   Spring Boot Modular Monolith
       ↓  JDBC / HikariCP
   PostgreSQL
```

Local: `ng serve` → `:4200`, Spring Boot → `:8080`, Docker Compose Postgres → `:5432`.

Initial production target (later deployment phases):

```text
Edge/CDN (e.g. Cloudflare) → Angular → Spring Boot → Managed PostgreSQL
```

No Redis, Kafka, workers, or AI SDKs in Stage 1.

## Modular monolith

One Spring Boot deployable (`quoteflow-backend`).

Feature packages are introduced **when features are built**, not as empty placeholders:

`auth`, `identity`/`user`, `business`, `customer`, `quotation`, `invoice`, `payment`, `subscription`, `entitlement`, `reporting`, `notification`, `email`, `storage`, `audit`, `analytics`, `ai`, `admin`, `common`, `security`, `config`

Module communication (when modules exist):

- explicit application services / interfaces
- domain events where appropriate
- **no** arbitrary cross-module repository access (e.g. Quotation must not write Invoice tables directly)

This keeps modules extractable into microservices later without requiring microservices now.

See [ADR-001](adr/ADR-001-modular-monolith-first.md).

## Multi-tenancy

Each **Business** is a tenant (`businesses` table). Each `app_users` row belongs to one business.

**IMPLEMENTED (Phase 2–3):** schema, FKs, global email uniqueness, tenant role/status CHECKs, JWT principal → trusted `businessId`. See [DATABASE.md](DATABASE.md), [AUTHENTICATION.md](AUTHENTICATION.md), [ADR-005](adr/ADR-005-tenant-isolation-strategy.md), [ADR-008](adr/ADR-008-authentication-and-token-strategy.md).

**IMPLEMENTED (Phase 5–8):** Object-level isolation on customers, quotations, and invoices (`findByIdAndBusiness_Id` / equivalent); cross-tenant → 404. Never trust client-supplied `businessId`.


## Data and schema

- PostgreSQL is the system of record — [ADR-002](adr/ADR-002-postgresql-primary-database.md)
- Flyway owns schema evolution; Hibernate `ddl-auto: validate`
- Migrations: V1 identity; V2 email CHECK; V3 customers; V4 quotations + document_sequences; V5 quotation business snapshot; V6 invoices + invoice_items + INVOICE sequences; V7 payments + RECEIPT sequences; V8 subscriptions + branding snapshots + `(business_id, created_at)` quota indexes
- HikariCP: Spring Boot default connection pool (no premature pool tuning)
- Money: BigDecimal / NUMERIC(19,4); authoritative `QuotationCalculator` (ADR-009)
- PDFs: on-demand OpenPDF from snapshots (ADR-012) — no object storage yet

## Infrastructure evolution (FUTURE)

| Stage | Add when justified |
|-------|--------------------|
| 1 (now) | Angular + Spring Boot + PostgreSQL |
| 2 | Redis, object storage, background processing, transactional outbox |
| 3 | Worker / AI service / API gateway if needed |
| 4 | Kafka / event streaming |
| 5 | Extract high-value modules to microservices |

**Triggers:** See cost and ops docs. Do not add infrastructure whose cost exceeds benefit.

### Redis (FUTURE)

Optional: cache, rate limits, short-lived state, distributed locks. PostgreSQL remains source of truth. Tenant-scoped cache keys required. Tolerate cache loss.

### Transactional outbox → Kafka (FUTURE)

Reliable async side effects: DB transaction writes business row + outbox event → processor → email/external (later Kafka). Consumers must be idempotent. No Kafka in MVP.

### Object storage (FUTURE)

`StorageService` abstraction (e.g. S3-compatible / R2) for logos, PDFs, attachments — not large blobs in PostgreSQL by default.

## Platform integrations (FUTURE)

```text
Spring Boot modular monolith
├── PostgreSQL          (IMPLEMENTED path)
├── Email Provider      (FUTURE)
├── BillingProvider     (FUTURE — QuoteFlow SaaS / Razorpay first)
├── PaymentProvider     (FUTURE — optional tenant invoice Pay Now)
├── PdfService          (FUTURE — quotations, invoices, receipts)
├── AI Provider         (FUTURE — see AI_ARCHITECTURE.md)
├── Storage Provider    (FUTURE — PDFs, logos, attachments)
├── Redis               (FUTURE — optional)
└── Event bus / Kafka   (FUTURE — after outbox when justified)
```

Core quotation/invoice flows must work when optional providers are down. **Platform billing ≠ tenant invoice payments** — see [PAYMENTS_ARCHITECTURE.md](PAYMENTS_ARCHITECTURE.md) and [DOCUMENTS_ARCHITECTURE.md](DOCUMENTS_ARCHITECTURE.md).


## Security direction (FUTURE)

Zero trust: network location never implies trust. Frontend / AI / gateway are not security boundaries.

- Spring Security, BCrypt, short-lived access tokens, revocable refresh tokens
- Roles: tenant `OWNER` / `ADMIN` / `STAFF` (+ `ACCOUNTANT` later) **and** object-level authorization
- Platform operator roles are separate (`PLATFORM_*`) — tenant `ADMIN` ≠ platform admin ([ADR-003](adr/ADR-003-separate-admin-and-ops-planes.md), [ADMIN_ARCHITECTURE.md](ADMIN_ARCHITECTURE.md))
- Explicit DTOs (no mass assignment to entities)
- CORS: explicit trusted origins only for authenticated APIs
- Minimal Actuator exposure; secrets via environment/secret managers only
- Target OWASP ASVS Level 2 for production SaaS baseline

Details: [SECURITY.md](SECURITY.md).

## Visibility planes (FUTURE)

1. **Tenant dashboard** — customer business metrics  
2. **Platform admin** — QuoteFlow SaaS/FinOps control (`/api/v1/platform-admin/**`)  
3. **Grafana / ops** — infrastructure health  

See [ADMIN_ARCHITECTURE.md](ADMIN_ARCHITECTURE.md) and [OPERATIONS.md](OPERATIONS.md).

## Observability (FUTURE expansion)

- Actuator `/actuator/health` (**IMPLEMENTED**)
- Micrometer → managed metrics/Grafana when justified; provider logs first; Loki/traces later
- Correlation IDs; no secrets or confidential document/AI prompt text in metrics/logs
- Do **not** self-host full Grafana/Prometheus/Loki stacks in MVP — see [OPERATIONS.md](OPERATIONS.md)

## Growth / marketing (FUTURE)

Public SEO pages, privacy-conscious analytics, acquisition funnels — separate from authenticated financial UI. See [GROWTH_ARCHITECTURE.md](GROWTH_ARCHITECTURE.md).

## Cost / FinOps

Early MVP fixed-cost target ~₹1,500–₹3,000/month before variable AI/payment/email. Platform cost/revenue dashboards are **platform-admin** concerns, not Grafana. See [COST_ARCHITECTURE.md](COST_ARCHITECTURE.md).

## Reusable SaaS platform intent

QuoteFlow is product #1. Keep platform capabilities (auth, tenancy, billing, entitlements, notifications, AI orchestration, audit, storage, platform admin, FinOps) separable from QuoteFlow-only domain logic.
