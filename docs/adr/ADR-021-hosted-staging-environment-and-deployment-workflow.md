# ADR-021: Hosted Staging Environment and Deployment Workflow

## Status

Accepted. Phase 16 hosted staging bring-up **CLOSED PASS** (2026-09-14): Railway staging backend + Postgres 18 + Cloudflare Pages `quoteflow-staging` with same-origin `/api` proxy. Not production approval.

## Context

Phase 15 delivered container artifacts and prod-like local compose. QuoteFlow needs a real hosted staging stack (Cloudflare Pages + Railway + managed PostgreSQL) before production release gates, without enabling real customer billing or uncontrolled email.

## Decision

1. **Staging ≠ production**: separate secrets, synthetic data, `BILLING_ENABLED=false`, `EMAIL_PROVIDER=DISABLED` by default.
2. **Security ≈ prod**: activate `SPRING_PROFILES_ACTIVE=prod,staging` (staging additive only).
3. **Deploy path**: Railway builds `backend/Dockerfile`; Cloudflare Pages builds Angular static output (`npm run build:staging`); Wrangler CLI deploy now; Git-triggered Pages once connected.
4. **Cookie topology**: same-origin `/api/*` Pages Function proxy to a **fixed** Railway origin (not an open proxy) so SameSite=Lax refresh cookies work on `*.pages.dev`. Do not casually set SameSite=None.
5. **Public API config**: `/config.json` runtime file — never secrets in the SPA; `Cache-Control: no-store`.
6. **Migrations**: Flyway on startup; Hibernate validate; no rewrite of applied versions.
7. **Rollback**: redeploy prior app artifact; DB restore/forward-fix separately.
8. **Cost**: single backend instance; no Redis/Kafka/Grafana mandatory.
9. **Providers**: Razorpay/Resend real E2E remain deferred unless explicitly executed later.

## Consequences

- Phase 16 hosted gates evidenced on real URLs (see [STAGING_VALIDATION.md](../STAGING_VALIDATION.md)).
- Preview `*.pages.dev` hostnames must not be wildcarded into credentialed CORS; Railway CORS is the exact Pages origin.
- Set-Cookie must be forwarded per-cookie from the proxy (`getSetCookie()`), or refresh cookies break.
