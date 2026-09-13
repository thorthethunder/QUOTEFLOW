# ADR-016 — SaaS Entitlement and Usage-Limit Strategy

- **Status:** Accepted
- **Date:** 2026-09-13
- **Phase:** 11

## Context

QuoteFlow needs Free / Pro / Business commercial limits without mixing platform billing into tenant Invoice/Payment tables, and without trusting the Angular client for enforcement. Razorpay is deferred to Phase 12.

## Decision

1. **Subscription entity separate from Business** — commercial entitlement lives in `subscriptions` (1:1 with Business).
2. **Code-defined plan catalog** (`PlanCatalog`) — no editable pricing CMS before Platform Admin.
3. **Server-authoritative checks** via `EntitlementService` + domain create paths; Angular gates are UX only.
4. **Tenant-level lock** — pessimistic lock on subscription row before quota check + create (same transaction).
5. **Phase 11 calendar-month quotas** in Business timezone on `created_at` (source-table counts; no dual usage counters).
6. **Downgrade** never deletes or hides historical data; only blocks new creates when over Free caps.
7. **Payments/receipts** never gated by quotation/invoice creation quotas.
8. **PDF branding snapshot** at SEND / payment record (`show_quoteflow_branding`).
9. **Provider integration deferred** — nullable provider columns; no checkout/webhooks yet.

## Alternatives

1. Store `plan` on Business — rejected: conflates identity with billing lifecycle.  
2. Frontend-only limits — rejected: trivial bypass.  
3. Redis counters — rejected: cost/complexity; source tables suffice.  
4. Current-plan PDF branding for historical docs — rejected for financial integrity; snapshot preferred.

## Consequences

- Clear separation from tenant payments ([ADR-004](ADR-004-platform-billing-vs-tenant-payments.md)).  
- Phase 12 updates subscription state through `SubscriptionService`, not ad-hoc Business fields.  
- Contended Free-plan creates serialize per tenant (acceptable for MVP).  
- Calendar-month policy may need period alignment when Razorpay billing periods arrive.

## References

[SUBSCRIPTIONS.md](../SUBSCRIPTIONS.md), [DOCUMENTS.md](../DOCUMENTS.md), V8 migration.
