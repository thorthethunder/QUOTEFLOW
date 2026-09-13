# QuoteFlow Security Test Matrix (Phase 14)

Practical OWASP ASVS Level 2–oriented mapping of controls to verification.
Status values: **PASS** (automated evidence), **PASS-MANUAL** (inspected this phase), **PARTIAL**, **DEFERRED**, **N/A**.

| Category | Threat | Module | Control | Automated test | Manual verification | Status | Residual risk |
|----------|--------|--------|---------|----------------|---------------------|--------|---------------|
| Authentication | Credential stuffing / weak hash | Auth | BCrypt strength 12; generic login errors | `AuthFlowIntegrationTest` | Inspect encoder config | PASS | No MFA / password reset |
| JWT | Forged/expired/wrong issuer tokens | Security | HS256 + require issuer/audience; ≥32-byte secret | `AuthFlowIntegrationTest.meRequiresValidJwt…` | Prod fail-closed validator | PASS | Access JWT valid until expiry after suspend |
| Refresh session | Replay / concurrent rotation | Auth | Hashed refresh; `FOR UPDATE`; rotate+revoke | `concurrentRefreshAllowsOnlyOneSuccess` | Cookie HttpOnly path | PASS | No global “revoke all sessions” UI |
| CSRF | Cookie session mutation CSRF | Auth refresh/logout | Cookie CSRF double-submit on refresh/logout | `Phase14SecuritySmokeIT` missing/invalid CSRF | Billing/email use Bearer (not cookie CSRF) | PASS | Bearer APIs rely on XSS resistance |
| CORS | Credentialed cross-origin abuse | Security | Explicit allow-list; no `*` with credentials | `corsAllowsConfiguredDevOrigin` hostile origin | Prod `CORS_ALLOWED_ORIGINS` required | PASS | Misconfigured prod origins |
| Authorization | Privilege escalation | Billing/domain | OWNER-only billing mutations; tenant roles | `BillingIntegrationTest` STAFF/ADMIN | Matrix documented | PASS | STAFF/ADMIN product matrix still coarse |
| Tenant isolation / BOLA | Cross-tenant IDOR | All domain | Lookup by id + `businessId` from JWT | `TenantIsolationSecurityIT` + module ITs | — | PASS | New endpoints must extend suite |
| Mass assignment | Client sets plan/totals/status | DTOs | Explicit request records; ignore protected fields | Module create/update tests | DTO review Phase 14 | PASS | Future DTOs need review |
| Input size | Oversized payloads / item floods | Quotes/invoices/lists | `@Size`, `MAX_ITEMS=100`, page size ≤100 | `Phase14SecuritySmokeIT.oversizedPageSizeRejected` | — | PASS | Server container body limit deployment-dependent |
| SQLi | Injected search/sort | Repositories/reporting | Parameter binding + sort allowlists | Reporting/quotation list tests | Code review | PASS | New dynamic SQL needs review |
| XSS | Stored/reflected script | Angular + email | Angular escaping; email HTML escape | Notification HTML escape IT; frontend templates | Representative render | PASS | CSP still allows style unsafe-inline |
| Email abuse | Burst send | Notifications | Entitlement + per-tenant/document rate limits | Notification ITs | In-memory limiter | PASS | Per-instance rate limit |
| PDF | XSS/SSRF via PDF | PDF services | Plain text OpenPDF; no remote fetch | Quotation/receipt PDF ITs | — | PASS | — |
| SSRF | User-controlled HTTP fetch | HTTP clients | Provider base URLs from config only | Architecture review | — | PASS | Keep clients config-only |
| Webhook forgery | Fake Razorpay events | Billing | HMAC over raw body; separate secret | `BillingIntegrationTest` | Real Razorpay E2E deferred | PASS | Live provider deferred |
| Financial concurrency | Overpayment / duplicate docs | Payments/docs | Row locks + sequences | Payment/invoice/quotation concurrency ITs | — | PASS | — |
| Quota races | Exceed Free limits | Subscriptions | Subscription row lock | Entitlement concurrency ITs | — | PASS | — |
| Secrets | Leakage in repo/logs | Config/CI | `.env` ignored; prod no weak JWT | Secret scan this phase | Rotate if ever leaked | PASS | Human error still possible |
| Headers | Clickjacking / sniffing | Security | nosniff, frame deny, CSP, Permissions-Policy, HSTS(prod) | `Phase14SecuritySmokeIT` | SPA CSP meta in `index.html` | PASS | Material needs style unsafe-inline |
| Actuator/OpenAPI | Info disclosure | Ops | Prod health-only; springdoc disabled | Prod YAML review | — | PASS | Dev swagger still open locally |
| Logging/PII | Secret/PII in logs | All | Structured events; no passwords/tokens | Code review | Ops discipline | PARTIAL | Occasional email domains in auth logs |
| Correlation ID | Support tracing | API | `X-Correlation-Id` + MDC | `Phase14SecuritySmokeIT` | — | PASS | Not a security boundary |
| Dependencies | Known CVEs | Maven/npm | Lockfiles; `npm ci`; `npm audit` | npm audit run Phase 14 | Maven CVE DB may be unavailable | PARTIAL | See audit section in report |
| Open redirect | Phishing via login returnUrl | Frontend auth | `safeReturnUrl` allowlists relative `/…` paths only | `safe-return-url.spec.ts` | — | PASS | Angular Router still UX; backend is authority |
| AI readiness | Tool bypass | Future | Tools → services only; human approval | Docs/ADR only | Not implemented | N/A | Future phase |

## Notes

- Angular route guards are **UX only**; backend remains authoritative.
- In-memory rate limits are **per JVM instance**, not globally distributed (Redis out of scope for Phase 14).
- Razorpay Test Mode E2E and Resend real-provider E2E remain **deferred** release gates.
