# ADR-003: Separate Tenant, Platform Admin, and Ops Visibility

- **Status:** Accepted
- **Date:** 2026-09-13
- **Phase:** 1 (decision only — implementation FUTURE)

## Context

QuoteFlow needs customer self-serve analytics, operator SaaS/FinOps control, and infrastructure monitoring. Combining these into one “admin” surface risks privilege confusion (tenant `ADMIN` ≠ platform admin) and leaks global/financial/ops data.

## Decision

Maintain **three planes**:

1. **Tenant dashboard** — business-scoped metrics for that tenant only  
2. **Platform admin dashboard** — QuoteFlow operator metrics (tenants, MRR, costs, AI spend) behind `/api/v1/platform-admin/**` and explicit `PLATFORM_*` roles  
3. **Grafana / operations** — infrastructure health (latency, errors, pools, deploy health)

Do not implement platform admin UI, Grafana, or Prometheus servers in Phase 1. Actuator remains the initial observability foundation.

## Alternatives

1. Single “admin” role for everything — rejected: privilege escalation and unclear boundaries.  
2. Put MRR/FinOps only in Grafana — rejected: wrong tool; business finance needs application-authorized APIs and audit.  
3. Self-host full observability stack in MVP — rejected: cost and ops overhead vs Stage 1 scale.

## Consequences

- Clear security and product boundaries; docs reserve ADMIN_* and OBSERVABILITY_* phases.  
- FinOps and Grafana evolve on independent timelines.  
- Engineers must not shortcut by exposing global queries on tenant APIs.
