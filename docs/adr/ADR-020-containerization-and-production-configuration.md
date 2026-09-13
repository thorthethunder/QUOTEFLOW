# ADR-020: Containerization and Production Configuration Strategy

## Status

Accepted (Phase 15)

## Context

QuoteFlow must be production-configurable and container-ready before cloud bring-up, without deploying yet. Target remains Cloudflare Pages (static Angular) + Railway/container backend + managed PostgreSQL. Cost discipline forbids Redis/Kafka/Grafana stacks at this stage.

## Decision

1. **Separate artifacts**: Spring Boot JAR image and Angular static site (Pages or optional nginx image for local smoke).
2. **Backend image**: multi-stage Maven build → `eclipse-temurin:21-jre-alpine`, non-root user, env-driven config, `PORT`/`0.0.0.0`, graceful shutdown, Actuator liveness/readiness (DB on readiness only).
3. **Proxy/TLS**: TLS at edge; `forward-headers-strategy=framework`; Secure cookies in prod; edge overwrites forwarded headers.
4. **Fail-closed prod**: JWT, CORS, billing (no FAKE), email (no CONSOLE/FAKE unless explicit staging override).
5. **Local prod-like compose**: `docker-compose.prod-local.yml` — does not replace `docker-compose.yml` (dev Postgres).
6. **Notification crash recovery**: reclaim stale `SENDING` rows after claim lease (uses `updated_at`; no V11 required).
7. **No Redis/Kafka/object storage** mandatory.
8. **Flyway** remains schema authority; Hibernate validate; migrations fail startup.

## Consequences

- Ordinary local dev (`ng serve` + Spring Boot + compose Postgres) stays available.
- Real Railway/Cloudflare deployment is Phase 16+.
- Operators must size Hikari pool × instances to managed Postgres limits.
- Razorpay/Resend real E2E remains deferred.
