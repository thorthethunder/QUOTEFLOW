# Deployment checklist

No secrets in this file.

## PRE-DEPLOY

- [ ] Backend `./mvnw test` green
- [ ] Backend `./mvnw package` green
- [ ] Frontend `npm ci` + `npm test` green
- [ ] `ng build --configuration=production` green
- [ ] Flyway change reviewed (never rewrite applied migrations)
- [ ] Env vars reviewed for target environment (`ENVIRONMENTS.md`)
- [ ] Staging backup considered before risky migration
- [ ] Release commit/SHA identified
- [ ] CORS origin matches **exact** frontend hostname
- [ ] Cookie topology: same-site domain **or** Cloudflare `/api` proxy configured
- [ ] `BILLING_ENABLED=false` unless payment validation intentionally in scope
- [ ] `EMAIL_PROVIDER=DISABLED` (or RESEND fully configured) — never CONSOLE in hosted staging

## DEPLOY

- [ ] Railway backend deploy from `backend/Dockerfile` (root directory `backend`)
- [ ] Managed Postgres attached (private networking preferred)
- [ ] Flyway applied; note version
- [ ] Readiness `/actuator/health/readiness` healthy
- [ ] Cloudflare Pages build (`frontend/`, Node 22, output `dist/frontend/browser`)
- [ ] Staging `robots.staging.txt` → `robots.txt` if non-indexable staging
- [ ] `config.json` / `_redirects` API proxy set for topology
- [ ] Frontend HTTPS + SPA deep-link refresh OK

## POST-DEPLOY

- [ ] Register/login/refresh/logout in real browser
- [ ] Secure + HttpOnly refresh cookie observed
- [ ] XSRF missing → 403; valid → success
- [ ] Hostile CORS origin has no ACAO
- [ ] Core synthetic flow (customer → quote → PDF → invoice → payment → dashboard)
- [ ] Plan page honest when billing disabled
- [ ] Email UX honest when DISABLED
- [ ] Actuator `/env` inaccessible; OpenAPI disabled
- [ ] Logs: no secrets; Flyway + startup.config present
- [ ] Restart/redeploy persistence smoke

## ROLLBACK DECISION

- Prefer redeploy previous known-good backend image/commit
- Frontend: Cloudflare previous deployment rollback
- DB: Flyway is forward-only — app rollback may be schema-incompatible; restore/forward-fix as needed
- Do not rewrite applied migrations
