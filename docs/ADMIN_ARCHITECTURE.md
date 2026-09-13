# QuoteFlow Platform Admin Architecture (FUTURE)

> **Status: FUTURE** for platform-admin UI/API; **Phase 2** documents tenant vs platform role separation (see ADR-006).  
> Do not confuse with the **customer/business dashboard**. Do not grant platform power via tenant `ADMIN`.

## Three visibility planes

| Plane | Audience | Answers |
|-------|----------|---------|
| **Tenant dashboard** | Business users (OWNER / ADMIN / STAFF) | “What is happening in *my* business?” |
| **Platform admin dashboard** | QuoteFlow operators only | “What is happening in the *QuoteFlow* business?” |
| **Grafana / operations** | Ops / SRE | “What is happening in *infrastructure*?” |

Keep these separate, separately authorized, and independently evolvable.

## Conceptual architecture

```text
Platform Administrator
        ↓
Admin UI  (/platform-admin — FUTURE)
        ↓
/api/v1/platform-admin/**
        ↓
Platform authorization (permissions + MFA — FUTURE)
        ↓
Platform / FinOps / Analytics services
        ↓
PostgreSQL (authoritative)
```

Tenant APIs must **never** expose global platform data.

Prefer namespace `/api/v1/platform-admin/**` over ambiguous `/api/v1/admin/**` because tenant role `ADMIN` already exists.

## Roles (strict separation)

**Tenant roles (customer product):** `OWNER`, `ADMIN`, `STAFF` (+ future `ACCOUNTANT`)

**Platform roles (operator control plane):**

- `PLATFORM_SUPER_ADMIN`
- `PLATFORM_OPERATIONS`
- `PLATFORM_FINANCE`
- `PLATFORM_SUPPORT`
- `PLATFORM_SECURITY`

A tenant `ADMIN` must **never** imply platform privileges.

**Phase 2 decision:** platform authorization will use a dedicated operator identity/permission model later — **not** additional values on `app_users.tenant_role`. See [ADR-006](adr/ADR-006-identity-and-role-separation.md).

### Example permissions (least privilege)

`PLATFORM_VIEW_METRICS`, `PLATFORM_VIEW_TENANTS`, `PLATFORM_MANAGE_TENANTS`, `PLATFORM_VIEW_BILLING`, `PLATFORM_MANAGE_SUBSCRIPTIONS`, `PLATFORM_VIEW_SECURITY`, `PLATFORM_SUPPORT_ACCESS`

Finance ≠ security ≠ support by default.

## Admin security (FUTURE)

- MFA strongly required before production platform admin is exposed (TOTP / WebAuthn)
- Stronger session requirements than customer sessions
- Dangerous actions: authorize + confirm + reason + audit
- No unrestricted silent impersonation; if support access exists: reason, audit start/end, visible banner, limited ops, expiry
- Break-glass access: time-limited, reason, audit, alert (mature systems only)

## Admin audit

Sensitive platform actions must be immutable to normal operators:

Examples: suspend/reactivate tenant, manual subscription change, refund, unlock account, support access, data export.

Record conceptually: `adminUserId`, `action`, `targetTenantId`, `targetUserId`, `timestamp`, `reason`, `correlationId`, `result`.

## Platform overview metrics (FUTURE)

Tenants: total / active / free / pro / business; new today / month  
Users: active, DAU, MAU, trials  
Subscriptions: paid, cancelled, churn

## Revenue (authoritative on backend)

MRR, ARR, new/expansion/contraction/churned MRR, collections, refunds, payment failures, ARPA/ARPU, free-to-paid (and trial-to-paid if used).

**Angular must not be the source of truth** for platform financial totals.

Sensitive finance APIs (conceptual):

- `/api/v1/platform-admin/finance/overview`
- `.../revenue`, `.../costs`, `.../margins`, `.../ai-costs`

Require platform finance/admin permission. Never expose to tenant admins. Exports (CSV/XLSX/PDF) authorized + audited; no permanent public URLs.

## FinOps / costs

See [COST_ARCHITECTURE.md](COST_ARCHITECTURE.md). Admin UI must not call cloud/AI/ad provider APIs directly — use collectors → normalized cost records → FinOps service.

## Tenant management (FUTURE)

Search/view tenants, plan, subscription, usage, AI usage, quotas, recent security events; suspend/reactivate with confirmation + reason + audit.

## Feature management (FUTURE)

Feature flags, entitlements, limits, AI availability, maintenance mode, beta flags — all audited.

## Platform health summary (convenience only)

High-level Healthy / Degraded / Down cards for API, DB, Redis, messaging, AI, email, payments, storage.

Detailed telemetry lives in **Grafana** — admin UI may link “Open Operations Dashboard” for authorized operators only. Do not embed unrestricted Grafana in tenant dashboards.

Platform finance views (MRR, costs, AI spend, gateway fees) must not include unlabeled tenant invoice collections — see [PAYMENTS_ARCHITECTURE.md](PAYMENTS_ARCHITECTURE.md).


## Analytics storage evolution

1. PostgreSQL reporting queries / aggregates / materialized views when measured need  
2. Events/ETL → warehouse only when volume justifies  

No data warehouse in MVP.

## Admin frontend (FUTURE Angular)

Lazy area `/platform-admin`: Overview, Tenants, Users, Subscriptions, Revenue, Costs, AI Usage, Infrastructure Overview, Security Events, Audit Logs, Feature Management, System Health.

Never show this nav to tenant users.

## Implementation phases (do not run ahead)

| Phase | Focus |
|-------|--------|
| ADMIN 1 | Platform role/security foundation |
| ADMIN 2 | Tenant overview |
| ADMIN 3 | Subscription/revenue metrics |
| ADMIN 4 | Cost/FinOps tracking |
| ADMIN 5 | AI cost dashboard |
| ADMIN 6 | Platform security/audit dashboard |

## Security tests (when implemented)

Tenant OWNER/ADMIN/STAFF cannot call platform-admin APIs; finance limited to finance APIs; unauthenticated rejected; admin ops audited.
