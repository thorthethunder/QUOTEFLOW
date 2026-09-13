# Staging validation log

**Status: HOSTED EXECUTION NOT RUN (Phase 16)**

This file records evidence for hosted staging. Items marked NOT RUN were blocked by missing provider access / git remote (see Phase 16 report).

| Check | Result | Evidence |
|-------|--------|----------|
| Railway backend deploy | NOT RUN | No `RAILWAY_TOKEN` / Railway CLI; no MCP |
| Cloudflare Pages deploy | NOT RUN | No Cloudflare API token / wrangler |
| Git-based deploy | NOT RUN | Local repo has **no commits** and no remote |
| HTTPS frontend/backend | NOT RUN | — |
| Flyway on managed Postgres | NOT RUN | — |
| Hosted login/refresh/logout | NOT RUN | — |
| Cookie SameSite topology | NOT RUN | Prep: same-origin `/api` proxy documented |
| CORS exact origin | NOT RUN | — |
| Hosted core E2E | NOT RUN | — |
| Container Trivy scan | NOT COMPLETED | Tool unavailable |
| Maven CVE scan | NOT COMPLETED | — |

## Preparation completed in-repo (not hosted evidence)

- Runtime `/config.json` API base loader
- Cloudflare `_redirects` SPA + same-origin API proxy example
- `SPRING_PROFILES_ACTIVE=prod,staging` + `EMAIL_PROVIDER=DISABLED`
- `backend/railway.toml` Dockerfile + readiness healthcheck hints
- Docs: ENVIRONMENTS, DEPLOYMENT_CHECKLIST, ADR-021

## When credentials are available

1. Create initial git commit + push to GitHub
2. Railway: new staging project + Postgres; root `backend`; Dockerfile deploy; env from checklist
3. Cloudflare Pages: project root `frontend`; Node 22; build `npm ci && npm run build`; output `dist/frontend/browser`
4. Configure `/api` proxy **or** same-site custom domains
5. Re-run this matrix and replace NOT RUN with PASS/FAIL + timestamps
