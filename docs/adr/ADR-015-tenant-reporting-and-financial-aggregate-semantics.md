# ADR-015 — Tenant Reporting and Financial Aggregate Semantics

## Status

Accepted (Phase 10)

## Context

Tenants need a Business Overview (dashboard) showing invoiced, collected, and outstanding figures derived from existing quotations, invoices, and payments — without inventing a second financial ledger or claiming accounting/GST compliance.

## Decision

1. **Read-only reporting module** (`com.quoteflow.reporting`) queries domain tables via dedicated `JdbcTemplate` aggregates. It does not write Customer/Quotation/Invoice/Payment rows.
2. **Authoritative sources remain domain tables.** Dashboard DTOs are derived views only.
3. **Terminology:** prefer **Invoiced / Collected / Outstanding** over “revenue” or “cash flow statement”.
4. **SENT invoices** participate in financial totals; **DRAFT** and **CANCELLED** do not (status counts may still be shown).
5. **RECORDED payments** contribute to collections; **VOIDED** do not.
6. **Payment state** for reporting uses the same mathematical rules as Phase 9 (`PaymentSummaryCalculator` semantics in SQL).
7. **Dates:** invoiced by `issue_date`; collected by `payment_date`; default period = current month in **Business timezone**.
8. **Mixed currencies** returned as `MoneyByCurrency[]` — never summed across currencies; **no FX**.
9. **No summary/materialized tables** in MVP; existing indexes suffice (no speculative V8).
10. **No historical as-of balance** reporting in Phase 10.

## Consequences

- Simple, testable SQL aggregates with strong tenant filters
- Clear product language that avoids accounting claims
- Multi-currency tenants see grouped totals
- Future gateway payments and platform billing remain separate concerns (ADR-004)
