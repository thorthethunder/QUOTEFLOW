# QuoteFlow

Multi-tenant SaaS for freelancers and small service businesses to manage customers, quotations, invoices, payments, receipts, and reports — with a path to AI-assisted workflows on a reusable platform foundation.

**Design for scale. Deploy for current scale.** Stage 1 is a modular monolith on PostgreSQL — not microservices, Redis, or Kafka.

## Current status

**Phases 1–16: CLOSED PASS** (hosted staging on Cloudflare Pages + Railway).  
**Phase 17:** production-readiness review — see [docs/PRODUCTION_READINESS.md](docs/PRODUCTION_READINESS.md).  
**Production launch:** NOT READY until domain/topology, backups, secrets, and provider final gates complete.  
**AI:** Phase 5 payment reminder assistant (human-approved send) + Phase 4 controlled actions + Business Copilot + Quote Assistant — [docs/AI_PAYMENT_REMINDERS.md](docs/AI_PAYMENT_REMINDERS.md), [docs/AI_ACTION_APPROVALS.md](docs/AI_ACTION_APPROVALS.md) (`AI_ENABLED=false`, `AI_ACTIONS_ENABLED=false` by default).

- Monorepo (`frontend/`, `backend/`, `docs/`, `.github/`)
- Angular auth shell, customers, quotations + PDF + email, invoices + reminders, payments + receipt PDF, dashboard, Plan & usage + checkout; Spring Boot modular monolith + Actuator
- PostgreSQL + Flyway V1–V11 (subscriptions, platform billing, notification outbox, AI action proposals)
- Hosted staging: same-origin `/api` Pages Function → Railway → private Postgres 18

Not implemented yet: tenant invoice Pay Now, refunds, Redis, Kafka, platform-admin UI, Grafana, object storage. Production AI deployment not started.

## Architecture (high level)

```text
Angular  →  REST/JSON  →  Spring Boot (modular monolith)  →  PostgreSQL
```

Visibility planes (FUTURE): tenant dashboard · platform admin / FinOps · Grafana ops — see docs below.

Financial domains: **platform billing** (money to QuoteFlow — Phase 12) vs **tenant payments** (money to the business — Phase 9 manual) — keep separate. See [docs/BILLING.md](docs/BILLING.md).

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/SECURITY.md](docs/SECURITY.md), [docs/AI_ARCHITECTURE.md](docs/AI_ARCHITECTURE.md).



## Technology

| Layer    | Stack                                      |
|----------|--------------------------------------------|
| Frontend | Angular 20, TypeScript, Angular Material   |
| Backend  | Java 21, Spring Boot 4.1, Maven            |
| Database | PostgreSQL 16 local / 18 staging (Railway) |
| Schema   | Flyway V1–V10                               |

## Prerequisites

- Java 21+
- Maven 3.9+ (or use `backend/mvnw`)
- Node.js 22+ and npm
- Docker Desktop (for PostgreSQL)

## Local setup

### 1. Environment file

```bash
cp .env.example .env
```

`.env` is gitignored. Compose defaults match development values in `.env.example` (`quoteflow` / `quoteflow`). **Development-only password — never reuse in production.** Production profile requires `DATABASE_*` environment variables with no weak defaults.

### 2. Start PostgreSQL

```bash
docker compose up -d
docker compose ps
```

### 3. Backend

```bash
cd backend
./mvnw test
./mvnw spring-boot:run
```

Windows:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Health check: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

### 4. Frontend

```bash
cd frontend
npm install
npm start
```

App: [http://localhost:4200](http://localhost:4200)

### Builds

```bash
# Backend
cd backend && ./mvnw -B test && ./mvnw -B -DskipTests package

# Frontend
cd frontend && npm ci && npm run build
```

## Useful URLs

| Service   | URL                                      |
|-----------|------------------------------------------|
| Frontend  | http://localhost:4200                    |
| Backend   | http://localhost:8080                    |
| Health    | http://localhost:8080/actuator/health    |

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Database](docs/DATABASE.md)
- [Authentication](docs/AUTHENTICATION.md)
- [Customers](docs/CUSTOMERS.md)
- [Quotations](docs/QUOTATIONS.md)
- [Invoices](docs/INVOICES.md)
- [Payments](docs/PAYMENTS.md)
- [Reporting / dashboard](docs/REPORTING.md)
- [Documents / Quotation PDF](docs/DOCUMENTS.md)
- [Frontend architecture](docs/FRONTEND_ARCHITECTURE.md)
- [Security](docs/SECURITY.md)
- [Platform admin / FinOps (FUTURE)](docs/ADMIN_ARCHITECTURE.md)
- [Operations / Grafana (FUTURE)](docs/OPERATIONS.md)
- [Billing / Razorpay SaaS (Phase 12)](docs/BILLING.md)
- [Notifications](docs/NOTIFICATIONS.md)
- [Email](docs/EMAIL.md)
- [Subscriptions & entitlements](docs/SUBSCRIPTIONS.md)
- [Payments & billing (FUTURE)](docs/PAYMENTS_ARCHITECTURE.md)
- [Documents architecture (future types)](docs/DOCUMENTS_ARCHITECTURE.md)
- [AI architecture](docs/AI_ARCHITECTURE.md)
- [AI Reporting Insights](docs/AI_REPORTING_INSIGHTS.md)
- [AI Business Knowledge](docs/AI_BUSINESS_KNOWLEDGE.md)
- [Cost architecture](docs/COST_ARCHITECTURE.md)
- [Growth architecture (FUTURE)](docs/GROWTH_ARCHITECTURE.md)
- [ADRs](docs/adr/)

## Next phase

Phase 17: Production Readiness and Release-Gate Review — **blocked until Phase 16 hosted staging PASS**.

Phase 16 hosted bring-up is NOT CLOSED pending Railway/Cloudflare access — see [STAGING_VALIDATION.md](docs/STAGING_VALIDATION.md), [DEPLOYMENT.md](docs/DEPLOYMENT.md), [ADR-021](docs/adr/ADR-021-hosted-staging-environment-and-deployment-workflow.md).

Docs: [ENVIRONMENTS](docs/ENVIRONMENTS.md) · [DEPLOYMENT_CHECKLIST](docs/DEPLOYMENT_CHECKLIST.md).

Razorpay Test Mode E2E and Resend real-provider E2E remain deferred release gates.
