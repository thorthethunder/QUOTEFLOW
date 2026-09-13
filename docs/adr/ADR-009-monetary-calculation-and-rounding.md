# ADR-009: Monetary Calculation and Rounding Strategy

## Status

Accepted (Phase 6)

## Context

Quotations introduce money. Frontend preview and backend persistence must not diverge silently.

## Decision

- Persist and authorize money with `BigDecimal` / `NUMERIC(19,4)`.
- Apply a single policy in `FinancialDocumentCalculator` (quotations use `QuotationCalculator` facade): scale **2**, `RoundingMode.HALF_UP`.
- Order: line amounts → subtotal → discount → tax on taxable → total.
- Ignore client-submitted totals; recalculate on every save.
- Invoices reuse the same calculator so converted documents match quotation totals exactly.

## Consequences

Deterministic tests; PDF/invoice phases reuse the same calculator. Not a claim of legal tax compliance.
