# ADR-017: Platform Billing and Razorpay Subscriptions

- **Status:** Accepted
- **Date:** 2026-09-13
- **Phase:** 12

## Context

Phase 11 delivered FREE/PRO/BUSINESS entitlements without checkout. Phase 12 must collect SaaS revenue via Razorpay Subscriptions without mixing tenant Invoice/Payment money, without trusting Angular for activation, and without requiring Razorpay for local FREE development.

## Decision

1. **Reuse** the single `subscriptions` row per business (extend with billing/provider fields).  
2. **`BillingProvider` abstraction** — Razorpay adapter isolated; FAKE provider for tests.  
3. **`BILLING_ENABLED`** gate — credentials not required when disabled.  
4. **Server-side plan mapping** — Angular never supplies Razorpay `plan_id` or amounts.  
5. **Checkout verify** uses checkout HMAC; **webhooks** (raw-body HMAC + event-id idempotency) are durable authority.  
6. **Activation:** grant paid entitlements for `authenticated|active|pending|halted|paused`; downgrade to FREE on `cancelled|completed|expired`.  
7. **Cancel-at-period-end** only in MVP; paid-to-paid changes deferred.  
8. **Separate** `billing_transactions` / `billing_webhook_events` from tenant `payments`.

## Alternatives

1. Create Razorpay Plan per checkout — rejected (platform config).  
2. Activate solely from Checkout UI success — rejected (fraud).  
3. Store SaaS charges in `payments` — rejected (ADR-004).

## Consequences

- Operators must create Razorpay Plans and webhooks manually.  
- Local/dev runs without Razorpay when billing disabled.  
- Complex proration/upgrades remain future work.

## References

- [BILLING.md](../BILLING.md)  
- [ADR-004](ADR-004-platform-billing-vs-tenant-payments.md)  
- [ADR-016](ADR-016-saas-entitlement-and-usage-limit-strategy.md)
