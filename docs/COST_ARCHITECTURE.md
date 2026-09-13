# QuoteFlow Cost Architecture

## Status

**FUTURE guidance** with Phase 1 cost posture recorded. No paid cloud spend required for local Phase 1.  
Platform FinOps UI is **not** implemented; reserved under [ADMIN_ARCHITECTURE.md](ADMIN_ARCHITECTURE.md).

## Principle

Cost is an architectural requirement. Design for scale; **deploy for current scale.**

Before adding infrastructure, answer:

1. Why now?
2. What problem does it solve today?
3. Fixed monthly cost?
4. Variable cost?
5. Free/usage-based alternative?
6. Failure impact?
7. Can current stack solve it?
8. At what scale is upgrading justified?

## IMPLEMENTED cost posture (Phase 1)

| Component | Cost posture |
|-----------|--------------|
| Angular | Local / later static hosting |
| Spring Boot | Local / later single service |
| PostgreSQL | Local Docker; later one managed instance |
| Redis / Kafka / K8s / multi-DB | **Not deployed** |
| Grafana / Prometheus / Loki | **Not deployed** — Actuator + provider logs first |

MVP fixed-cost **target** after first deploy: roughly **₹1,500–₹3,000/month** before variable AI, payment fees, high email volume, taxes, domain renewal. Target ≠ guarantee.

## Platform admin vs tenant finance

| Dashboard | Shows |
|-----------|--------|
| Platform admin | QuoteFlow subscription revenue, gateway fees, failed SaaS charges, MRR/ARR |
| Tenant dashboard | That business’s invoiced / collected / outstanding only |

Never mix domains. Details: [PAYMENTS_ARCHITECTURE.md](PAYMENTS_ARCHITECTURE.md), [ADMIN_ARCHITECTURE.md](ADMIN_ARCHITECTURE.md).

## FUTURE FinOps pipeline


```text
Cloud / AI / Payment / Ads / Email providers
        ↓
Cost collectors (API / CSV / manual entry)
        ↓
Normalized PlatformCostRecord store
        ↓
FinOps service (backend authoritative)
        ↓
Platform Admin finance APIs  →  Admin dashboard
```

Do **not** couple Admin UI directly to provider billing APIs. Do not fabricate missing costs. Grafana monitors infra health; it is not the MRR/margin ledger.

### Conceptual cost record

`PlatformCostRecord`: provider, service, category (`INFRASTRUCTURE`, `DATABASE`, `CACHE`, `MESSAGING`, `STORAGE`, `AI`, `EMAIL`, `PAYMENTS`, `MONITORING`, `MARKETING`, `OTHER`), amount, currency, usage quantity/unit, period, external reference, metadata.

Preserve original amount/currency; normalize to reporting currency with rate source/date when converting.

### Cost categories (dashboard)

Infrastructure (hosting, Postgres, Redis, Kafka, storage, CDN, egress, monitoring, backups), AI (tokens, embeddings, external AI), communication (email/SMS/etc.), payments (gateway fees, refunds, chargebacks), growth (ads, affiliates), operations (domain, SaaS tools, security, support).

### Allocation maturity

1. Platform-level costs first  
2. Later: per tenant / user / feature / AI request when data justifies it  

Avoid over-engineered allocation early.

### Profitability / cash (estimates, not statutory accounts)

Revenue − (infra + AI + comms + payment fees + ads + ops SaaS) → contribution / gross margin % and cost ratios. Cash inflow vs outflow views are management estimates — export to proper accounting for tax/legal.

### AI cost visibility

Requests/tokens/spend by day/month, tenant, plan, feature, model; expensive tenants/features; margin after AI. Enforce quotas in product code so AI cannot create uncontrolled bills.

### Cost alerts (configurable later)

Budget thresholds (e.g. 50/75/90/100%), AI/infra/egress/email/ad spikes — actionable, not noisy.

## FUTURE cost triggers

| Capability | Introduce when | Cheaper default until then |
|------------|----------------|----------------------------|
| Redis | Measured cache/rate-limit/coordination need | App + DB only |
| Object storage | Logos/PDFs/attachments | Defer files; avoid large blobs in PG |
| Workers / outbox | Long-running or reliable async side effects | Sync where safe and short |
| Kafka | Event volume / many consumers / replay needs | In-process events → outbox first |
| Managed Grafana / metrics | Production need for dashboards/alerts | Actuator + host logs |
| Self-hosted full observability | Economics justify vs managed | Managed/free tier |
| Microservices / K8s | Independent scale, isolation, team ownership | Modular monolith |
| Multiple AI providers | Proven product need | One provider behind abstraction |

## Variable costs to watch later

- AI tokens / provider bills (enforce quotas)
- Payment processing fees
- Email volume
- Egress / storage
- Observability cardinality / log retention
- Advertising spend

Do not add trackers, AI providers, brokers, or observability servers in Phase 1 “because the architecture mentions them.”
