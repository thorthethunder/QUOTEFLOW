# Production readiness (Phase 17)

**Status:** Phase 17 review complete — see verdict at end of this document and the Phase 17 report in chat history.  
**This is not a production deploy authorization.**

No secrets belong in this file.

## Application foundation verdict

| Dimension | Result |
|-----------|--------|
| Application / platform foundation | **CONDITIONAL PASS** |
| Production launch | **NOT READY** |
| AI implementation | **NOT STARTED** (AI Phase 1 follows) |

## Staging baseline (authoritative)

| Item | Value |
|------|--------|
| Git SHA (Phase 16 close) | `31d6dc2` |
| Git SHA (Phase 17 review) | See latest `main` after Phase 17 commit |
| Frontend | `https://quoteflow-staging.pages.dev` |
| Backend | `https://quoteflow-backend-staging.up.railway.app` |
| Topology | Pages → same-origin `/api` Function → Railway → private Postgres 18.6 |
| Flyway | V1–V10 |
| Billing / Email | DISABLED |

## Gate categories

| Symbol | Meaning |
|--------|---------|
| **PASS** | Verified in repo and/or staging |
| **BLOCKER** | Must resolve before claiming foundation PASS |
| **REQUIRED BEFORE PROD** | Must complete before production traffic |
| **DEFERRED ACCEPTED** | Known residual; accepted for foundation with explicit final gate |
| **NOT TESTED** | Not executed in this environment |

## Gate matrix

| Gate | Status | Notes |
|------|--------|-------|
| Backend `./mvnw test` | **PASS** | 110 tests, 0 failures (local Postgres required) |
| Backend `./mvnw package` | **PASS** | |
| Frontend tests | **PASS** | 52 SUCCESS |
| Frontend production build | **PASS** | Initial ~360 kB raw / ~103 kB transfer |
| GitHub Actions CI | **NOT TESTED** | `gh` unavailable; private Actions not verified |
| Flyway V1–V10 integrity | **PASS** | Immutable migrations; tenant FKs; money NUMERIC |
| PostgreSQL 18 compatibility | **PASS** | Staging 18.6 + Flyway/Hibernate validate |
| Tenant isolation | **PASS** | API IDOR → 404; `TenantIsolationSecurityIT` |
| Auth / refresh / XSRF / cookies | **PASS** | Hosted staging evidence Phase 16 |
| Authorization (tenant RBAC) | **DEFERRED ACCEPTED** | OWNER-only billing enforced; broader OWNER/ADMIN/STAFF API RBAC not implemented — document as product decision |
| CORS / proxy | **PASS** | Fixed-origin Function; `BACKEND_ORIGIN` env for prod |
| Security headers | **PASS** | Backend + Pages `_headers` + SPA CSP meta |
| Input validation / mass assignment | **PASS** | DTO validation; client businessId ignored |
| Financial integrity | **PASS** | Backend BigDecimal; UI preview is non-authoritative |
| Concurrency (numbers, convert, quotas) | **PASS** | Locks + unique constraints + ITs |
| Idempotency (webhooks / convert) | **PASS** | Platform webhooks + quote→invoice |
| Tenant payment request idempotency | **DEFERRED ACCEPTED** | Documented gap; balance lock mitigates races |
| Notification outbox | **DEFERRED ACCEPTED** | At-least-once; ambiguous timeout duplicate window documented |
| Billing security (code) | **PASS** | Disabled in staging; fail-closed prod validators |
| Razorpay Test Mode E2E | **REQUIRED BEFORE PROD** | FINAL PAYMENT VALIDATION |
| Email architecture | **PASS** | DISABLED / RESEND fail-closed |
| Resend domain E2E | **REQUIRED BEFORE PROD** | If external email required for launch |
| PDF security | **PASS** | Tenant-scoped; private/no-store; plain text |
| Rate limiting | **PASS** | Auth endpoints; per-instance only |
| Error handling / logging / PII | **PASS** | No stack traces to clients; correlation IDs |
| Secret scan (repo) | **PASS** | No live secrets found (test fakes only) |
| npm audit (prod deps) | **PASS** | 0 vulnerabilities |
| Maven OWASP / NVD CVE DB | **NOT TESTED** | NVD API key missing; scan incomplete |
| Container image scan (Trivy) | **NOT TESTED** | Tool/DB not available |
| Dockerfiles | **PASS** | Non-root backend; multi-stage |
| Production config fail-closed | **PASS** | Requires `prod` profile (+ optional `staging`) |
| Railway healthcheckPath (platform) | **REQUIRED BEFORE PROD** | Declared in `railway.toml`; live field may be unset — set in dashboard |
| Backup / PITR / restore drill | **REQUIRED BEFORE PROD** | Staging PITR was disabled; production must enable + test restore |
| Production domain / cookie topology | **REQUIRED BEFORE PROD** | Prefer same-origin `/api` or same-site custom domains; never casual SameSite=None |
| Production secrets | **REQUIRED BEFORE PROD** | Unique JWT, DB, provider secrets |
| Staging noindex leak | **REQUIRED BEFORE PROD** | Production must not use `build:staging` robots overlay / staging `X-Robots-Tag` |
| Firefox / Safari / iOS / Android | **NOT TESTED** | Chromium-only hosted evidence |
| Native 200% zoom | **NOT TESTED** | |
| Safe load / capacity | **NOT TESTED** | No aggressive load against shared staging |
| AI / Ollama | **NOT STARTED** | See [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md) |

## REQUIRED BEFORE PRODUCTION (checklist)

1. **Domains:** Choose production frontend + API topology (same-origin proxy or same-site custom domains). Update CORS to exact frontend origin(s).
2. **Cloudflare production Pages project** (separate from `quoteflow-staging`): Node 22, `npm ci`, production build **without** staging robots overlay, set `BACKEND_ORIGIN` to production Railway HTTPS origin, noindex **off**.
3. **Railway production environment** (not staging): new Postgres with **backups/PITR enabled**, private networking, Dockerfile deploy, env vars, platform healthcheck `/actuator/health/readiness`.
4. **Secrets:** unique `JWT_SECRET`, DB credentials, Razorpay/Resend when enabling those features.
5. **Profiles:** `SPRING_PROFILES_ACTIVE=prod` (never `staging` alone).
6. **Razorpay Test Mode E2E** — see Final Payment Validation checklist below.
7. **Resend** (if launch requires email) — domain + SPF/DKIM + send E2E.
8. **CI green** on release SHA (verify in GitHub Actions UI).
9. **CVE/container scans** with working vulnerability DBs; triage HIGH+ before launch.
10. **Restore drill** from production backup policy; record RPO/RTO.

## Razorpay Test Mode — FINAL PAYMENT VALIDATION

Do not mark PASS until executed against Test Mode credentials:

- [ ] Checkout starts (OWNER only)
- [ ] Client/server signature verify
- [ ] Webhook signature verify (raw body)
- [ ] Duplicate webhook → idempotent
- [ ] Out-of-order webhook safe
- [ ] Subscription activates entitlement
- [ ] Cancel updates entitlement
- [ ] Failed payment does not grant PRO incorrectly
- [ ] Restart/retry does not double-charge / double-apply
- [ ] Secrets never in frontend or logs

## Resend — FINAL EXTERNAL-INTEGRATION VALIDATION

Required only if production launch depends on outbound email:

- [ ] Verified sending domain
- [ ] SPF / DKIM configured; DMARC recommended
- [ ] Send quotation / reminder happy path
- [ ] Provider failure → outbox FAILED + retry behavior
- [ ] Ambiguous timeout residual understood (at-least-once)
- [ ] Unsubscribe/legal requirements as applicable

## Recommended production topology

```text
https://app.example.com          Cloudflare Pages (SPA)
https://app.example.com/api/*    Pages Function → Railway (BACKEND_ORIGIN)
                                 SameSite=Lax refresh cookie on app.example.com

OR same-site pair:
https://app.example.com
https://api.example.com          (requires careful cookie Domain / SameSite design)
```

**Do not** ship `pages.dev` ↔ `railway.app` cross-site cookies with SameSite=Lax.

## Railway production plan (no deploy in Phase 17)

- Environment: `production` (separate from staging)
- Service: Dockerfile from `backend/`
- Healthcheck: `/actuator/health/readiness` (dashboard + `railway.toml`)
- Postgres: managed, private network, backups on
- Vars: `SPRING_PROFILES_ACTIVE=prod`, `DATABASE_*` JDBC, `JWT_*`, `CORS_ALLOWED_ORIGINS`, `BILLING_*`, `EMAIL_*`, `PORT`
- Flyway: auto on startup; never rewrite applied versions
- Rollback: previous deployment; DB restore/forward-fix separately

## Cloudflare production plan (no deploy in Phase 17)

- Separate project from `quoteflow-staging`
- Build: production Angular build (**not** `build:staging` robots overlay)
- `BACKEND_ORIGIN` = production API HTTPS origin
- Headers: security headers **without** permanent staging noindex
- Rollback: previous Pages deployment

## Notification delivery decision

**Accepted for launch foundation:** PostgreSQL outbox is **at-least-once**. Ambiguous provider timeout may duplicate sends unless the provider honors idempotency keys (Resend). Do not claim exactly-once. Product copy must not imply stronger guarantees.

## Tenant RBAC decision

**Accepted deferred:** Fine-grained OWNER/ADMIN/STAFF enforcement beyond billing checkout is not implemented server-wide. All authenticated tenant users share CRUD for core resources today. Expand with `@PreAuthorize` before multi-role enterprises need isolation inside a business.

## Backup / recovery targets (must be set in production)

| Metric | Guidance |
|--------|----------|
| RPO | ≤ 24h minimum; prefer continuous/PITR if Railway plan allows |
| RTO | Document operator restore steps; drill once before launch |
| Staging | PITR may remain off; never assume staging = production backup |

## Related docs

- [STAGING_VALIDATION.md](STAGING_VALIDATION.md) · [DEPLOYMENT.md](DEPLOYMENT.md) · [ENVIRONMENTS.md](ENVIRONMENTS.md)  
- [SECURITY.md](SECURITY.md) · [OPERATIONS.md](OPERATIONS.md) · [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md)  
- ADR-021 hosted staging · ADR-018 email outbox · ADR-004 billing vs tenant payments
