# ADR-021: Hosted Staging Environment and Deployment Workflow

## Status

Accepted (Phase 16 preparation). **Hosted bring-up not executed** in the environment that authored this ADR (no Railway/Cloudflare credentials; repository had no git commits/remote).

## Context

Phase 15 delivered container artifacts and prod-like local compose. QuoteFlow still needs a real hosted staging stack (Cloudflare Pages + Railway + managed PostgreSQL) before production release gates, without enabling real customer billing or uncontrolled email.

## Decision

1. **Staging ≠ production**: separate secrets, synthetic data, `BILLING_ENABLED=false`, `EMAIL_PROVIDER=DISABLED` by default.
2. **Security ≈ prod**: activate `SPRING_PROFILES_ACTIVE=prod,staging` (staging additive only).
3. **Deploy path**: Railway builds `backend/Dockerfile`; Cloudflare Pages builds Angular static output; Git-triggered deploys once a remote exists.
4. **Cookie topology**: prefer same registrable domain, or Cloudflare same-origin `/api/*` reverse proxy to Railway so SameSite=Lax refresh cookies work. Do not casually set SameSite=None.
5. **Public API config**: `/config.json` runtime file (and optional build-time env) — never secrets in the SPA.
6. **Migrations**: Flyway on startup; Hibernate validate; no rewrite of applied versions.
7. **Rollback**: redeploy prior app artifact; DB restore/forward-fix separately.
8. **Cost**: single backend instance; no Redis/Kafka/Grafana mandatory.
9. **Providers**: Razorpay/Resend real E2E remain deferred unless explicitly executed later.

## Consequences

- Phase 16 **cannot PASS** until hosted gates are evidenced on real URLs.
- Preview `*.pages.dev` hostnames must not be wildcarded into credentialed CORS.
- Operators must complete `STAGING_VALIDATION.md` when provider access exists.
