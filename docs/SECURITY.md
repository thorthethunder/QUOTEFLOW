# QuoteFlow Security

## Status

| Area | State |
|------|--------|
| Phase 1 baseline hardening | **IMPLEMENTED** |
| Identity/tenant schema | **IMPLEMENTED** (Phase 2) |
| Authentication / JWT / refresh | **IMPLEMENTED** (Phase 3) |
| Angular auth / cookie CSRF | **IMPLEMENTED** (Phase 4) |
| Tenant customer isolation | **IMPLEMENTED** (Phase 5) |
| Tenant quotation isolation + authoritative money | **IMPLEMENTED** (Phase 6) |
| Quotation PDF tenant isolation + snapshot integrity | **IMPLEMENTED** (Phase 7) |
| Invoice isolation + conversion uniqueness | **IMPLEMENTED** (Phase 8) |
| Payment isolation + overpayment/concurrency | **IMPLEMENTED** (Phase 9) |
| Dashboard/reporting tenant aggregates | **IMPLEMENTED** (Phase 10) |
| Platform subscription entitlements / quota races | **IMPLEMENTED** (Phase 11) |
| Full OWASP ASVS L2 controls | **PHASE 14 BASELINE** — see [SECURITY_TEST_MATRIX.md](SECURITY_TEST_MATRIX.md), [ADR-019](adr/ADR-019-production-security-and-assurance-baseline.md) |
| Platform billing webhooks | **IMPLEMENTED** (Phase 12) — live Razorpay E2E deferred |
| Transactional email | **IMPLEMENTED** (Phase 13) — real Resend E2E deferred |
| Hosted staging security | **PASS** (Phase 16) — see [STAGING_VALIDATION.md](STAGING_VALIDATION.md) |
| Production launch readiness | **NOT READY** — gates in [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md) |
| AI Quote Assistant | **IMPLEMENTED** (AI Phase 2) — [QUOTE_ASSISTANT.md](QUOTE_ASSISTANT.md); AI_ENABLED=false by default |
| AI Business Copilot | **IMPLEMENTED** (AI Phase 3) — read-only tools; [BUSINESS_COPILOT.md](BUSINESS_COPILOT.md) |
| AI controlled actions | **IMPLEMENTED** (AI Phase 4) — proposal → human confirm; [AI_ACTION_APPROVALS.md](AI_ACTION_APPROVALS.md); AI_ACTIONS_ENABLED=false by default |
| AI payment reminders | **IMPLEMENTED** (AI Phase 5) — recipient/payment authority + approved outbox send; [AI_PAYMENT_REMINDERS.md](AI_PAYMENT_REMINDERS.md) |

## IMPLEMENTED (Phase 1–3)

- Secrets not committed; `.env` gitignored; `.env.example` placeholders only
- Angular environments contain only public config (`apiBaseUrl`, `appName`)
- Actuator exposure limited (`health`/`info` locally; `health` only in `prod`)
- Production profile requires `DATABASE_*` and secure `JWT_SECRET` (no weak defaults)
- Hibernate `ddl-auto: validate`; Flyway owns schema (V1 + V2 email canonical CHECK)
- `password_hash` only; BCrypt strength 12; refresh tokens store SHA-256 `token_hash` only
- Tenant roles (`OWNER`/`ADMIN`/`STAFF`) mapped to `ROLE_*` authorities — never platform roles
- Auth endpoints: register/login/refresh/logout; trusted tenant from JWT principal
- CORS allow-list with credentials; CSRF on cookie-backed refresh/logout; Bearer for APIs
- Generic login errors; in-memory auth rate-limit foundation (single-instance)
- Security headers: `X-Content-Type-Options`, deny framing, `Referrer-Policy: no-referrer`

Details: [AUTHENTICATION.md](AUTHENTICATION.md), [ADR-008](adr/ADR-008-authentication-and-token-strategy.md).

## Residual risks (Phase 3)

| Risk | Notes |
|------|--------|
| No MFA / password reset / email verification | Intentional; later phases |
| Access JWT valid until short expiry after suspension | Acceptable MVP tradeoff; refresh re-checks status |
| Refresh token JSON still allowed for API clients | Browsers use HttpOnly cookie; `return-in-body` for tests/API |
| In-memory rate limit | Not distributed; edge/WAF later |
| No multi-device session UI | Logout-all can revoke all refresh rows later |

## Phase 6 quotation controls

- Tenant lookups: `quotationId + businessId`; cross-tenant customer attach → 404
- Ignore client totals / status / quotationNumber / businessId on create-update DTOs
- Lifecycle via `/send` and `/cancel` only; SENT not ordinarily updatable
- Concurrent numbering via locked `document_sequences`; optimistic draft `@Version` → 409

## Phase 7 PDF controls

- `GET /api/v1/quotations/{id}/pdf` authenticated; cross-tenant → 404
- On-demand render from persisted snapshots/totals; no HTML input; no remote fetches
- `Cache-Control: private, no-store`; safe filename from quotation number
- DRAFT/CANCELLED status visible on PDF

## Phase 8 invoice controls

- Tenant lookups: `invoiceId + businessId`; conversion uses `quotationId + businessId` → cross-tenant 404
- One quotation → one invoice: DB `UNIQUE(source_quotation_id)` + pessimistic quotation lock
- Conflict code `QUOTATION_ALREADY_INVOICED` (409); ignore client totals / invoiceNumber / status / businessId
- SENT/CANCELLED invoices immutable server-side; optimistic lock 409 on DRAFT
- Conversion copies quotation snapshots (not live Customer/Business); do not log snapshot PII

## Phase 9 payment controls

- Payments tenant-scoped; cross-tenant payment/receipt → 404
- Invoice row lock on record/void; `PAYMENT_EXCEEDS_BALANCE` / `INVOICE_NOT_PAYABLE` / `INVOICE_HAS_PAYMENTS`
- Receipt PDF authenticated, snapshot-based, `private, no-store`; no PII in logs
- No silent payment edit/delete; void is explicit

## Phase 10 reporting controls

- `GET /api/v1/dashboard/summary` tenant-scoped via JWT `businessId`; parameterized SQL
- Cross-tenant leakage blocked by `business_id` filters on every aggregate
- Mixed currencies never summed; no FX service
- Invalid date ranges → `INVALID_DATE_RANGE`; range length capped
- No PII in Actuator metric labels; payment notes not exposed on dashboard recent payments

## Phase 11 subscription / entitlement controls

- Backend authoritative quotas; Angular plan object has zero authority
- No tenant mass-assignment of plan/status/provider fields; no production plan-switch API
- Cross-tenant entitlement isolation via JWT `businessId`
- Tenant subscription row lock prevents Free quota races
- Monthly quotas use Business timezone Instant bounds on `created_at` (not backdatable `issue_date`)
- Downgrade does not delete data; payments/receipts remain available at exhausted quotas
- PDF branding authority from server snapshot / entitlement — not client flags
- Platform MRR/ARR must never appear on tenant dashboard

## Phase 14 assurance

- Dedicated suites: `TenantIsolationSecurityIT`, `Phase14SecuritySmokeIT`
- Correlation ID filter (`X-Correlation-Id` + MDC)
- Sensitive API `Cache-Control: private, no-store`
- Headers: nosniff, frame deny, CSP (`frame-ancestors 'none'` on API), Permissions-Policy; HSTS on `prod` only
- SPA CSP meta in `frontend/src/index.html` (Razorpay checkout origins; Material style `unsafe-inline`)
- Incident playbook: [INCIDENT_RESPONSE.md](INCIDENT_RESPONSE.md)
- Matrix: [SECURITY_TEST_MATRIX.md](SECURITY_TEST_MATRIX.md)

## FUTURE controls

- Password reset / change; email verification; MFA (especially platform-admin)
- Platform `PLATFORM_*` roles and MFA — see [ADMIN_ARCHITECTURE.md](ADMIN_ARCHITECTURE.md)
- Full audit_logs persistence; Maven CVE DB in CI when network allows
- Distributed rate limiting (edge/WAF or Redis) when multi-instance requires it
- CSP style nonces (reduce `unsafe-inline`)

## Platform billing webhooks (Phase 12)

`POST /api/v1/webhooks/razorpay` is not JWT-authenticated. Auth is Razorpay HMAC (`X-Razorpay-Signature`) over the **raw body**. CSRF does not apply to this provider callback. Do not broaden CORS for webhooks. Never log API/webhook secrets or full signatures. See [BILLING.md](BILLING.md).

## Transactional email (Phase 13)

Recipient addresses come from tenant-scoped document snapshots, not client authority. HTML email content escapes untrusted fields. Provider secrets stay server-side. Rate limits apply per tenant/document. See [NOTIFICATIONS.md](NOTIFICATIONS.md) and [EMAIL.md](EMAIL.md).

## Phase 16 — hosted staging

Hosted Railway/Cloudflare bring-up requires provider credentials and a git remote. Until then, evidence lives in [STAGING_VALIDATION.md](STAGING_VALIDATION.md) as **NOT RUN**. Cookie/CORS topology must use same-site domains or a Cloudflare same-origin `/api` proxy — not cross-site provider hostnames with SameSite=Lax.

## Phase 15 — containers

Docker/production packaging is documented in [DEPLOYMENT.md](DEPLOYMENT.md), [DOCKER.md](DOCKER.md), and [ADR-020](adr/ADR-020-containerization-and-production-configuration.md). Production fail-closed for JWT/CORS/billing/email providers. No secrets in images.

See master architecture directive. Do not claim production-ready until that checklist is evidenced.

## Future AI trust boundary (not implemented)

```text
AI Tool → Spring Business Service → Authentication / Authorization / Tenant checks
         → Validation / Business rules / Transaction → Database
```

Never: AI Tool → Repository. High-impact actions (email send, void, delete, billing) require human approval.
Phase 4 implements prepare → review → confirm for draft quotation/invoice and reminder prepare (no send).
See [AI_ACTION_APPROVALS.md](AI_ACTION_APPROVALS.md), [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md) and ADR-019.

### Future AI threat register (document only)

| Threat | Note |
|--------|------|
| Prompt injection | Treat model output as untrusted input |
| Tool abuse | Tools only call authorized services |
| Cross-tenant retrieval | Tenant from principal, not prompt |
| Hallucinated IDs | Resolve IDs server-side with authz |
| Data exfiltration | Least-privilege tool scopes |
| Malicious knowledge docs | Curate / isolate RAG later |
| Cost abuse | Quotas before AI phase |
| Approval bypass | Side effects need explicit user confirm |

## AI Phase 6 reporting insight controls

- `POST /api/v1/ai/insights/analyze` is authenticated and derives tenant from JWT principal only.
- No SQL, JPQL, repository, EntityManager, or JdbcTemplate is exposed to AI.
- Reporting facts are calculated by `ReportingService` / `ReportingRepository`; AI narrative is non-authoritative.
- Mixed currencies remain separate; no FX or combined total is invented.
- Top invoices/customers are bounded and returned as evidence references for trusted frontend navigation.
- Recent dashboard records are omitted from insight responses to minimize business data exposure.
- AI disabled/unavailable/timeout returns facts and warnings rather than blocking reporting.
