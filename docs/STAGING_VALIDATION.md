# Staging validation log

**Status: Phase 16 NOT CLOSED — hosted bring-up incomplete**

Updated after git remote push (`2f57490` on `main`).

| Check | Result | Evidence |
|-------|--------|----------|
| Git commits + remote + push | **PASS** | `main` @ `2f57490` → `origin` https://github.com/thorthethunder/QUOTEFLOW |
| CI green | **NOT VERIFIED** | Private repo; Actions UI/API not readable without GitHub login in this agent session |
| Railway backend deploy | **NOT RUN** | `railway whoami` → Unauthorized; no `RAILWAY_TOKEN` |
| Cloudflare Pages deploy | **NOT RUN** | No Cloudflare API token / wrangler login |
| HTTPS frontend/backend | **NOT RUN** | — |
| Flyway on managed Postgres | **NOT RUN** | — |
| Hosted login/refresh/logout | **NOT RUN** | — |
| Cookie SameSite topology | **NOT RUN** | Prep: same-origin `/api` proxy documented |
| CORS exact origin | **NOT RUN** | — |
| Hosted core E2E | **NOT RUN** | — |
| Container Trivy scan | **NOT COMPLETED** | Tool unavailable |
| Maven CVE scan | **NOT COMPLETED** | — |

## Preparation completed in-repo (not hosted evidence)

- Runtime `/config.json` API base loader
- Cloudflare `_redirects` SPA + same-origin API proxy example
- `SPRING_PROFILES_ACTIVE=prod,staging` + `EMAIL_PROVIDER=DISABLED`
- `backend/railway.toml` Dockerfile + readiness healthcheck hints
- Docs: ENVIRONMENTS, DEPLOYMENT_CHECKLIST, ADR-021

## To finish Phase 16

1. Confirm GitHub Actions CI green on `main` (in browser while signed in)
2. `railway login` (or set `RAILWAY_TOKEN`) → create staging project + Postgres → deploy `backend/` Dockerfile with env from DEPLOYMENT.md
3. Cloudflare Pages: connect repo, root `frontend`, Node 22, configure `/api` proxy or same-site domains
4. Re-run hosted auth/CORS/cookie/core flow matrix and replace NOT RUN with PASS/FAIL + timestamps
