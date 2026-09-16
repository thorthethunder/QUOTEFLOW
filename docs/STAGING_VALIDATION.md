# Staging validation log

**Status: Phase 16 CLOSED PASS** (hosted staging bring-up complete — not production approval)

Phase 17 production-readiness gates: [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md).

Final hosted revision (frontend): Cloudflare Pages `quoteflow-staging` @ git `main` after Phase 16 Task 3 commit (see report SHA). Backend Railway staging previously green at `290c4ef` + env CORS update.

| Check | Result | Evidence |
|-------|--------|----------|
| Git commits + remote + push | **PASS** | `main` → `origin` https://github.com/thorthethunder/QUOTEFLOW |
| CI green | **NOT VERIFIED** | Private repo Actions not readable from this agent session |
| Railway backend deploy | **PASS** | `quoteflow-backend` Online · `https://quoteflow-backend-staging.up.railway.app` |
| Railway PostgreSQL | **PASS** | Postgres 18.6 · private `*.railway.internal` · no public TCP proxy |
| Flyway on managed Postgres | **PASS** | V1–V10 applied; restart stays at v10 |
| Hibernate validate | **PASS** | Startup with `ddl-auto=validate` |
| Cloudflare Pages deploy | **PASS** | Project `quoteflow-staging` · `https://quoteflow-staging.pages.dev` |
| HTTPS frontend/backend | **PASS** | Pages + Railway HTTPS; no mixed content |
| Same-origin `/api` proxy | **PASS** | Pages Function `functions/api/[[path]].ts` → fixed Railway origin |
| Hosted login/refresh/logout | **PASS** | Chromium E2E 2026-09-14 |
| Cookie SameSite topology | **PASS** | `qf_refresh` HttpOnly+Secure+SameSite=Lax+Path=/api/v1/auth on Pages host |
| XSRF | **PASS** | missing/wrong → 403; valid refresh/logout → OK |
| CORS exact origin | **PASS** | Railway `CORS_ALLOWED_ORIGINS=https://quoteflow-staging.pages.dev` (browser traffic same-origin via proxy) |
| Hosted core E2E | **PASS** | customer → quote → PDF → invoice → payments → receipt → dashboard/plan |
| FREE plan limit | **PASS** | 6th customer → `PLAN_LIMIT_REACHED` 403 |
| Cross-tenant | **PASS** | 404 |
| Rate limit | **PASS** | auth login 429 (per-instance) |
| Staging noindex | **PASS** | `robots.txt` Disallow + `X-Robots-Tag: noindex, nofollow` |
| Container Trivy scan | **NOT COMPLETED** | Tool unavailable historically |
| Maven CVE scan | **NOT COMPLETED** | — |

## Topology (authoritative)

- Frontend: `https://quoteflow-staging.pages.dev`
- Browser API: same-origin `/api/v1` → Pages Function → `https://quoteflow-backend-staging.up.railway.app`
- `config.json`: `{ "apiBaseUrl": "/api/v1" }` with `Cache-Control: no-store`
- Do **not** use direct `pages.dev` ↔ `railway.app` credentialed cookies without proxy

## Deferred forever until final gates

- Razorpay Test Mode E2E → FINAL PAYMENT VALIDATION  
- Resend real-provider/domain E2E → FINAL EXTERNAL-INTEGRATION VALIDATION  
