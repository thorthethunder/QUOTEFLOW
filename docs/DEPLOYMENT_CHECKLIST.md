# Deployment checklist

No secrets in this file.

## PRE-DEPLOY

- [ ] Backend `./mvnw test` green
- [ ] Backend `./mvnw package` green
- [ ] Frontend `npm ci` + `npm test` green
- [ ] Production Angular build green (`npm run build` — **not** staging robots overlay for production)
- [ ] Flyway change reviewed (never rewrite applied migrations)
- [ ] Env vars reviewed for target environment (`ENVIRONMENTS.md`)
- [ ] Staging/production backup considered before risky migration
- [ ] Release commit/SHA identified; CI verified if accessible
- [ ] CORS origin matches **exact** frontend hostname
- [ ] Cookie topology: same-site domain **or** Cloudflare `/api` proxy (`BACKEND_ORIGIN` set)
- [ ] `SPRING_PROFILES_ACTIVE=prod` (or `prod,staging` for staging only — never `staging` alone)
- [ ] `BILLING_ENABLED=false` unless payment validation intentionally in scope
- [ ] `EMAIL_PROVIDER=DISABLED` (or RESEND fully configured) — never CONSOLE in hosted environments

## DEPLOY (staging reference)

- [ ] Railway backend deploy from `backend/Dockerfile` (root directory `backend`)
- [ ] Managed Postgres attached (private networking preferred)
- [ ] Platform healthcheck path `/actuator/health/readiness`
- [ ] Flyway applied; note version
- [ ] Readiness healthy
- [ ] Cloudflare Pages (`frontend/`, Node 22, output `dist/frontend/browser`)
- [ ] Staging: `npm run build:staging` for noindex robots; production: do **not**
- [ ] `config.json` + Function proxy / `BACKEND_ORIGIN` set
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

## PRODUCTION LAUNCH GATES

See [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md). Do not launch while REQUIRED BEFORE PROD items remain open (domains, backups, secrets, Razorpay/Resend final E2E as applicable, CI, scans).

## ROLLBACK DECISION

- Prefer redeploy previous known-good backend image/commit
- Frontend: Cloudflare previous deployment rollback
- DB: Flyway is forward-only — app rollback may be schema-incompatible; restore/forward-fix as needed
- Do not rewrite applied migrations
