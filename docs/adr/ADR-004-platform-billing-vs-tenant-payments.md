# ADR-004: Separate Platform Billing from Tenant Payments

- **Status:** Accepted
- **Date:** 2026-09-13
- **Phase:** 1 (decision only — implementation FUTURE)

## Context

QuoteFlow collects SaaS subscription revenue and also helps tenants record/collect payments on their own invoices. Mixing these domains corrupts MRR, FinOps, tenant dashboards, and reconciliation.

## Decision

Treat **platform billing** (`BillingProvider`, subscriptions, entitlements) and **tenant business payments** (manual and/or `PaymentProvider`, invoice balance, receipts) as separate domains with separate records, dashboards, and metrics.

Authoritative payment/subscription state is determined only after backend verification and/or verified webhooks — never from Angular checkout success alone.

PDF/documents render authoritative DB values; they are not a substitute for financial records.

## Alternatives

1. Single “Payment” module for SaaS and tenant money — rejected: accounting and security confusion.  
2. Trust frontend payment success — rejected: fraud and integrity risk.  
3. Dedicated Payment/Document microservices in MVP — rejected: premature cost and complexity.

## Consequences

- Clear FinOps vs tenant revenue reporting ([ADMIN_ARCHITECTURE.md](../ADMIN_ARCHITECTURE.md)).  
- Implementation phases remain: invoices/manual payments before Razorpay SaaS billing before optional tenant Pay Now.  
- Engineers must not grant entitlements or mark invoices paid without verified provider/manual flows.
