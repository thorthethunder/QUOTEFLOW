# QuoteFlow Payments

## Status

| Area | State |
|------|--------|
| Manual payment recording | **IMPLEMENTED** (Phase 9) |
| Partial / full payments | **IMPLEMENTED** |
| Authoritative balance / payment state | **IMPLEMENTED** |
| Receipt numbering + PDF | **IMPLEMENTED** |
| Payment void | **IMPLEMENTED** |
| Payment gateways / Pay Now (tenant invoice) | **FUTURE** (not Phase 12 — platform SaaS billing only) |
| Refunds / credit notes | **FUTURE** |

Generic manual payment recording only — **not** accounting/GST/bank reconciliation compliance.

Dashboard **Collected** metrics (Phase 10) sum RECORDED payments by `payment_date` and exclude VOIDED — see [REPORTING.md](REPORTING.md).

Payment recording and receipt PDF remain available when Free plan creation quotas are exhausted (Phase 11 safety rule). Platform SaaS billing is separate — see [SUBSCRIPTIONS.md](SUBSCRIPTIONS.md).

## Core model

Invoice `totalAmount` is a fixed historical obligation.

Payments are separate immutable financial records (except explicit VOID).

```text
Invoice total
  ↓ Payment* (RECORDED)
authoritative aggregate
  ↓
amountPaid / balanceDue / paymentState
```

Document status (`DRAFT` / `SENT` / `CANCELLED`) is **independent** of payment state (`UNPAID` / `PARTIALLY_PAID` / `PAID`).

## Payability

| Invoice status | Payments |
|----------------|----------|
| DRAFT | Rejected (`INVOICE_NOT_PAYABLE`) |
| SENT | Allowed |
| CANCELLED | Rejected |

Invoice with active `RECORDED` payments cannot be cancelled (`INVOICE_HAS_PAYMENTS`). After all payments are voided, cancel may proceed.

## Aggregation

`PaymentSummaryCalculator`:

- `amountPaid` = sum of RECORDED payment amounts
- `balanceDue` = invoice total − amountPaid
- state from those amounts (scale 2, HALF_UP)

Frontend must not authoritatively submit aggregates.

## Overpayment / concurrency

Payment amount must be `> 0` and `≤ balanceDue`.

Recording locks the invoice row (`SELECT … FOR UPDATE`) inside one transaction so concurrent payments cannot both validate against the same balance.

## Receipt numbering

`document_sequences` type `RECEIPT`, format `RCP-000001`, unique per tenant. Voided receipts keep their number (not reused).

## Historical receipt amounts

Frozen at payment creation:

- `invoice_total_at_payment`
- `previous_paid_amount`
- `remaining_balance_after_payment`
- `invoice_number_snapshot`

Receipt PDF uses Invoice customer/business snapshots (immutable once SENT), not live Customer/Business.

## Void

`POST /api/v1/payments/{id}/void` marks `VOIDED` with optional reason / audit fields. Does not delete. VOIDED payments contribute 0 to aggregates. Receipt PDF shows VOIDED.

## Idempotency

Phase 9 does **not** implement `Idempotency-Key`. Frontend must not auto-retry ambiguous creates. Concurrency + overpayment checks remain mandatory. Formal idempotency deferred for gateway hardening.

## API

| Method | Path |
|--------|------|
| POST | `/api/v1/invoices/{invoiceId}/payments` |
| GET | `/api/v1/invoices/{invoiceId}/payments` |
| GET | `/api/v1/payments/{paymentId}` |
| POST | `/api/v1/payments/{paymentId}/void` |
| GET | `/api/v1/payments/{paymentId}/receipt.pdf` |

Error codes: `INVOICE_NOT_PAYABLE`, `PAYMENT_EXCEEDS_BALANCE`, `PAYMENT_ALREADY_VOIDED`, `INVOICE_HAS_PAYMENTS`.

## Methods

`CASH` · `BANK_TRANSFER` · `UPI_MANUAL` · `CHEQUE` · `OTHER`

`UPI_MANUAL` is a manual record of an external transfer — not a verified gateway payment.

## Future gateway

Manual Payment ≠ Gateway Payment. Future Razorpay/etc. must verify signatures/webhooks and reconcile idempotently into Payment records. Never trust frontend “payment succeeded.”

See [ADR-014](adr/ADR-014-payment-recording-and-balance-model.md), [PAYMENTS_ARCHITECTURE.md](PAYMENTS_ARCHITECTURE.md).
