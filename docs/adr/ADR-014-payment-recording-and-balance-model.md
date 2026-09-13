# ADR-014: Payment Recording and Balance Model

## Status

Accepted (Phase 9)

## Context

Invoices need partial payments, authoritative balances, and correctable mistakes without conflating document lifecycle with payment state or trusting the UI for money aggregates.

## Decision

1. **Payments are source of truth** for amounts collected; invoice total remains fixed historical obligation.
2. **Separate lifecycles** — invoice document status vs derived payment state (`UNPAID` / `PARTIALLY_PAID` / `PAID`).
3. **Partial payments allowed**; overpayment rejected (`PAYMENT_EXCEEDS_BALANCE`).
4. **Derived balance** via `PaymentSummaryCalculator` over `RECORDED` payments (BigDecimal, scale 2, HALF_UP).
5. **Concurrency** — pessimistic lock on invoice during record/void; no Java `synchronized`, no Redis.
6. **Immutability** — no silent update/delete; corrections via explicit `VOIDED` status with audit fields.
7. **Receipts** — every payment gets immutable `RCP-######`; historical balance fields frozen at payment time; PDF from invoice snapshots.
8. **Gateway separation** — manual records now; provider verification/idempotency later (Phase 12). Phase 9 omits request idempotency keys (document risk; prevent UI auto-retry).

## Consequences

Safe concurrent manual collection and deterministic receipts. Ambiguous network retries remain a residual risk until idempotency keys land with gateway work.
