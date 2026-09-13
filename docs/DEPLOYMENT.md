# QuoteFlow Deployment

Phase 15 delivered containers. Phase 16 prepares **hosted staging** (Cloudflare Pages + Railway + managed PostgreSQL).

**Hosted bring-up status:** see [STAGING_VALIDATION.md](STAGING_VALIDATION.md). Do not treat local Docker as hosted PASS.

## Target architecture

```text
Internet
  → Cloudflare Pages (Angular static, HTTPS)
  → same-origin /api proxy  OR  api-staging.<domain>
  → Railway Spring Boot (HTTPS at edge)
  → Managed PostgreSQL (private networking preferred)
```

## Git / deploy workflow

| Branch | Target |
|--------|--------|
| `staging` (recommended) or protected preview | Hosted staging |
| `main` | Future production candidate |

Requirements before Git deploy works:

1. Initial commit(s) on the repository  
2. Remote (e.g. GitHub) connected to Railway + Cloudflare Pages  

Approval: human reviews CI green before promoting `main`.

## Railway backend (staging)

1. Create a **staging** Railway project (separate from future production).  
2. Add **PostgreSQL** plugin/service (Postgres 16 preferred).  
3. Add web service:
   - **Root Directory:** `backend`
   - **Builder:** Dockerfile (`backend/Dockerfile`, see `backend/railway.toml`)
   - **Healthcheck:** `/actuator/health/readiness`
4. Set variables (secrets in Railway Variables — never commit):

```text
SPRING_PROFILES_ACTIVE=prod,staging
PORT=<injected>
DATABASE_URL=jdbc:postgresql://...   # convert from postgres:// if needed; prefer private host
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
JWT_SECRET=<strong unique staging secret ≥32 bytes>
JWT_ISSUER=quoteflow
JWT_AUDIENCE=quoteflow-api
CORS_ALLOWED_ORIGINS=https://<exact-staging-frontend-origin>
BILLING_ENABLED=false
EMAIL_PROVIDER=DISABLED
EMAIL_WORKER_ENABLED=false
HIKARI_MAXIMUM_POOL_SIZE=5
```

5. Confirm private DB networking when available; do not expose Postgres publicly.  
6. SSL: follow Railway Postgres defaults (typically require SSL on public URLs; private network may differ — record actual mode in STAGING_VALIDATION).

## Cloudflare Pages (staging)

| Setting | Value |
|---------|--------|
| Root directory | `frontend` |
| Node version | **22** (pin in dashboard / `.nvmrc`) |
| Install | `npm ci` |
| Build | `npm run build` (= production Angular build) |
| Output | `dist/frontend/browser` |
| SPA | `public/_redirects` → `/* /index.html 200` |

### API base URL strategy (Cloudflare-compatible)

**Recommended:** keep `config.json` → `{ "apiBaseUrl": "/api/v1" }` and add a **same-origin reverse proxy** line in `_redirects` (see `_redirects.same-origin-api.example`) so the browser talks only to the Pages host. Refresh cookies stay first-party (SameSite=Lax).

**Alternative:** same-site custom domains (`staging.` + `api-staging.`) with absolute `apiBaseUrl` in `config.json`.

**Do not:** credentialed CORS to `*.pages.dev` wildcards; do not bake localhost into hosted `config.json`.

Staging robots: copy `robots.staging.txt` → `robots.txt` in the Pages build command if the project should be noindex.

### Headers / CSP

Prefer Cloudflare Transform Rules / Headers for CSP/nosniff/Referrer-Policy when ready. Until then, `index.html` meta CSP remains; avoid duplicate conflicting policies.

## Cookie / CORS critical rules

- Exact `CORS_ALLOWED_ORIGINS` = frontend origin (scheme + host + port if any).  
- Cross-site `pages.dev` ↔ `railway.app` without proxy = **auth cookie failure** → Phase 16 blocker.  
- Never “fix” by disabling Secure or blindly setting SameSite=None.

## Rollback

- Backend: redeploy previous Railway deployment / git SHA.  
- Frontend: Cloudflare deployment rollback.  
- Database: Flyway forward-only; restore from backup or forward-fix — not automatic rollback.

## Related docs

- [DOCKER.md](DOCKER.md) · [ENVIRONMENTS.md](ENVIRONMENTS.md) · [DEPLOYMENT_CHECKLIST.md](DEPLOYMENT_CHECKLIST.md)  
- [STAGING_VALIDATION.md](STAGING_VALIDATION.md) · [ADR-021](adr/ADR-021-hosted-staging-environment-and-deployment-workflow.md)
