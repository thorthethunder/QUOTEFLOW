# QuoteFlow Operations & Observability

## IMPLEMENTED

- **Phase 15 containers** — [DOCKER.md](DOCKER.md), [DEPLOYMENT.md](DEPLOYMENT.md), [ADR-020](adr/ADR-020-containerization-and-production-configuration.md)
- **Phase 16 hosted staging** — [ENVIRONMENTS.md](ENVIRONMENTS.md), [DEPLOYMENT_CHECKLIST.md](DEPLOYMENT_CHECKLIST.md), [STAGING_VALIDATION.md](STAGING_VALIDATION.md), [ADR-021](adr/ADR-021-hosted-staging-environment-and-deployment-workflow.md)
- **Phase 17 production readiness** — [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md) (launch gates, backup/RPO targets, topology)
- **AI Phase 1** — [AI_LOCAL_DEVELOPMENT.md](AI_LOCAL_DEVELOPMENT.md); AI optional (`AI_ENABLED=false` default); Ollama outage must not affect readiness
- Spring Boot Actuator with minimal exposure (`/actuator/health`; probes: liveness/readiness in prod)
- Production profile: `health` only, `show-details: never`
- **Phase 12 platform billing ops** — see [BILLING.md](BILLING.md) for Razorpay Test/Live setup, webhook endpoint, env vars (`BILLING_ENABLED`, plan IDs, secrets)
- **Phase 13 email ops** — see [EMAIL.md](EMAIL.md): `EMAIL_PROVIDER`, `EMAIL_FROM`, optional `RESEND_API_KEY`, domain SPF/DKIM/DMARC before production send
- **AI Phase 5 payment reminders** — [AI_PAYMENT_REMINDERS.md](AI_PAYMENT_REMINDERS.md): human-approved send via durable outbox; local enqueue idempotent per proposal; external provider remains **at-least-once**
- **Phase 14 security ops** — correlation IDs in logs; [INCIDENT_RESPONSE.md](INCIDENT_RESPONSE.md); [SECURITY_TEST_MATRIX.md](SECURITY_TEST_MATRIX.md)

## Backup / restore readiness (targets, not guarantees)

| Item | Target (internal) | Notes |
|------|-------------------|--------|
| RPO | ≤ 24h initially | Prefer provider automated Postgres backups |
| RTO | ≤ 4h initially | Document restore steps with host |
| Restore testing | Quarterly | Restore into isolated instance and smoke-test login + one quotation |
| Retention | ≥ 7–30 days | Set with hosting provider |

**Container filesystem backup is not business-data backup.** Rely on managed PostgreSQL backups.

Do not invent SLA guarantees. Confirm backup job exists and one restore drill is recorded before public launch.

## Platform billing runbook (summary)

1. Create Razorpay Plans (Test then Live) matching `PlanCatalog` prices  
2. Configure webhook to `POST /api/v1/webhooks/razorpay` with subscription events  
3. Set secrets via env/secret manager — never commit  
4. Enable `BILLING_ENABLED=true` only when config is complete  
5. Production fails startup if billing enabled but Razorpay config incomplete  

Safe log events: `billing.checkout.*`, `billing.webhook.*`, `billing.subscription.*` — never secrets or full signatures.

> Grafana answers infrastructure health. It is **not** the SaaS revenue/FinOps dashboard.

## Principle

```text
TENANT DASHBOARD     → business of the customer
PLATFORM ADMIN       → business of QuoteFlow (MRR, costs, tenants)
GRAFANA / OPS        → production infrastructure health
```

Do **not** self-host Grafana + Prometheus + Loki + Tempo + Kafka + Redis as six servers in MVP. Prefer provider-native monitoring or managed/low-cost observability until economics justify more.

## Target pipeline (FUTURE)

```text
Spring Boot
   ├── Micrometer
   └── OpenTelemetry (when useful)
           ↓
Metrics / Logs / Traces pipeline
   ├── Prometheus (or managed metrics)
   ├── Loki / logs (or provider logs first)
   └── Tracing (when distributed)
           ↓
        Grafana  →  Ops dashboards + alerts
```

## Observability stages

| Stage | Capability |
|-------|------------|
| OBS 1 | Actuator + Micrometer fundamentals (**partially started**: Actuator only; optional billing counters when Micrometer fits) |
| OBS 2 | Managed metrics / Grafana integration |
| OBS 3 | API / JVM / DB dashboards |
| OBS 4 | Alerting (actionable INFO/WARNING/CRITICAL) |
| OBS 5 | Centralized logs |
| OBS 6 | Distributed tracing when architecture requires it |
| OBS 7 | Redis / Kafka / multi-service dashboards **when those systems exist** |

## Metrics (FUTURE — Micrometer)

HTTP count/latency/errors; JVM memory/heap/GC/threads/CPU; Hikari pool usage/wait; cache hit/miss when Redis exists; job/queue backlog; AI and external provider latency; payment webhook failures; email failures.

### API / deployment dashboards

RPS, p50/p95/p99, 2xx/4xx/5xx, slow/failing endpoints, instance count, CPU/memory/heap/GC, pool saturation, release version / deploy time / error+latency before vs after.

Safe build metadata (internal): `applicationVersion`, `gitCommit`, `buildTime`, `environment` — do not over-expose repository metadata publicly.

### PostgreSQL

Availability, connections/pool saturation, query latency, locks/deadlocks, storage growth, txn rate, backup status (via provider). **Never put DB passwords in Grafana.**

### Payment / subscription provider metrics (FUTURE)

When billing/payments exist: checkout creation rate, provider latency/failures, webhook receive / signature failure / processing errors, pending payment age, success rate, refund errors. **No customer PII or document text in metric labels.**

Business MRR and tenant invoice collections belong in **platform admin** and **tenant dashboards** respectively — not as a substitute for Grafana. See [PAYMENTS_ARCHITECTURE.md](PAYMENTS_ARCHITECTURE.md).

### Redis / Kafka

Only when deployed. Monitor availability, memory/lag/errors — do not deploy Redis/Kafka just for charts.

## Logging

Structured JSON where appropriate: timestamp, severity, service, environment, `correlationId` / `traceId`. Tenant/user IDs only when needed and safe.

**Never log:** passwords, tokens, API keys, payment secrets, DB credentials, sensitive document/AI prompt contents.

Start with provider logs; centralize (e.g. Loki) later. Control retention, sampling, levels, cardinality — production default is not DEBUG.

## Alerting (FUTURE)

Actionable only: API down, 5xx/p95 spikes, DB unavailable/pool saturation, memory/CPU/storage pressure, webhook/email failures, queue lag, Redis/Kafka issues when present, AI error/cost spikes, deployment regressions.

Classify INFO / WARNING / CRITICAL. Avoid alert fatigue.

## SLI / SLO

Define after production measurements exist (availability, latency, error rate, critical workflow success). Do not invent unrealistic SLOs early.

## Grafana & Prometheus security

- No public anonymous Grafana
- Strong auth; MFA/SSO when available; viewer vs admin roles; TLS
- No secrets in dashboard variables
- Metrics labels must **not** include customer email/name, invoice/quotation text, AI prompts (cardinality + privacy)
- Prefer aggregates; IDs only when justified
- Protect `/actuator/prometheus` if exposed; never public unauthenticated scrape of sensitive endpoints

## Deployment verification

Success ≠ “container started.” Verify: health, DB connectivity, migrations, API/frontend smoke, auth flow, critical workflows, error rate, latency.

## Cost of observability

Follow FinOps: retention, sampling, cardinality control. See [COST_ARCHITECTURE.md](COST_ARCHITECTURE.md).

## Integration with platform admin

Platform admin may show a **high-level** Operations card and a link to Grafana for authorized operators. Detailed infra stays in Grafana. See [ADMIN_ARCHITECTURE.md](ADMIN_ARCHITECTURE.md).

## AI reporting insights operations

Reporting Insights is optional and uses the same `AI_ENABLED` provider switch. The deterministic reporting API remains usable
when AI is disabled or unavailable. Tune per-instance limits with:

```text
AI_REPORTING_INSIGHTS_MAX_MESSAGE=1000
AI_REPORTING_INSIGHTS_MAX_OUTSTANDING_ROWS=5
AI_REPORTING_INSIGHTS_RATE_USER=6
AI_REPORTING_INSIGHTS_RATE_TENANT=20
```

Do not log prompts, full datasets, customer notes, or invoice text. Usage telemetry records feature, provider/model,
latency, success/failure, and tenant/user presence only.
