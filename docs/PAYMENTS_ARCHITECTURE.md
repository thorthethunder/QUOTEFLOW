# QuoteFlow Payments & Billing Architecture (FUTURE)

> **Status: FUTURE — not implemented in Phase 1.**  
> No Razorpay SDK, payment tables, or checkout flows yet. Preserve this separation when Phases 8–12+ land.

## Two financial domains (non-negotiable)

| Domain | Who is paid | Examples | Module path |
|--------|-------------|----------|-------------|
| **Platform billing** | QuoteFlow | Plan upgrades, subscriptions, AI credits, add-ons | `BillingProvider` → subscription / entitlements |
| **Tenant business payments** | The QuoteFlow customer’s business | Client pays a freelancer’s ₹25,000 invoice | `PaymentProvider` / manual payment → invoice reconciliation |

Do **not** mix QuoteFlow MRR with tenant invoice collections in the same totals unless explicitly labeled (platform admin vs tenant dashboard).

```text
QUOTEFLOW PLATFORM                         TENANT BUSINESS
Customer SaaS subscription                 Tenant's customer
        ↓                                          ↓
   Razorpay (BillingProvider)                   Invoice
        ↓                                          ↓
   Billing Service                          Pay Now / Manual
        ↓                                          ↓
   Subscription / Entitlements              PaymentProvider (verified)
        ↓                                          ↓
   QuoteFlow revenue                        Tenant payment record
                                                   ↓
                                            Invoice reconciliation
```

## Provider abstractions

Do not scatter provider SDKs through quotation/invoice services.

Conceptual interfaces (exact shapes in implementation phases):

- **`BillingProvider`** — QuoteFlow SaaS checkout/subscription (initial: `RazorpayBillingProvider`)
- **`PaymentProvider`** — optional online collection for tenant invoices

Possible operations: `createCheckout`, `createOrder`, `createSubscription`, `cancelSubscription`, `verifySignature`, `verifyWebhook`, `fetchPayment`, `refund`, `fetchSubscription`.

Do not over-generalize before a real second provider exists. Keep modules extractable later (Payment / Billing / Document services) without extracting in MVP.

## Authoritative payment state

**Wrong:** Angular `paymentSuccess = true` → activate subscription / mark invoice paid.

**Correct:** Provider checkout → backend verification and/or verified webhook (and/or provider query) → payment transaction → business state update.

Server decides final state. Frontend success UI is never authoritative.

## Security

Never store: card number, CVV, UPI PIN, bank credentials, provider auth secrets.

Never expose to Angular: `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` (or equivalents).

Publishable/public key IDs only if the provider requires them client-side.

Prefer hosted/provider checkout experiences.

Public pay pages: **high-entropy tokens** (`/pay/{securePublicToken}`), not sequential IDs (`/pay/12345`). Support expiry/revocation, limited scope, tenant-safe lookup.

## Webhooks

Verify signature (raw body when required), reject invalid, idempotent processing, persist provider event IDs, handle duplicates and safe retries, transactional updates, audit. Never trust payload before verification.

Conceptual `PaymentProviderEvent`: provider, `providerEventId`, type, status, received/processed times, processing status, retry count, sanitized payload reference — avoid retaining highly sensitive full payloads.

Same event five times must not create five payments, five activations, or five receipts.

## Tenant payment model (conceptual)

`id`, `businessId`, `invoiceId`, `amount`, `currency`, `paymentDate`, `paymentMethod`, `status`, `provider`, `providerPaymentId`, `providerOrderId`, `reference`, `notes`, timestamps.

**Money:** `BigDecimal` only; explicit scale/rounding; explicit currency (default may be INR; no silent multi-currency totals).

### Manual payments (required even without gateway)

`CASH`, `BANK_TRANSFER`, `UPI_MANUAL`, `CHEQUE`, `OTHER` → partial/full paid balances without online checkout.

### Online “Pay Now” (later)

Secure public invoice page → backend order → provider checkout → verified webhook → payment + balance + receipt + notify.

### Lifecycle states (map carefully from provider docs)

e.g. `CREATED`, `PENDING`, `AUTHORIZED`, `CAPTURED`, `FAILED`, `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED`.

## Reconciliation

Compare provider amount/currency/state to application invoice/payment. Detect missing, duplicate, amount/currency mismatch, refund, failed capture, orphan provider payments.

Do not rely on webhooks alone long-term — periodic reconciliation jobs for stale `PENDING` payments.

## Refunds (later)

Authorize → policy → confirmation if high risk → provider → verified result → refund record → reconcile → audit. Conceptual `Refund` entity when needed.

## Platform purchases & entitlements

Subscriptions / AI credits / add-ons / seats / limits use billing + **entitlement** service after **verified** payment — never because checkout UI succeeded.

Subscription conceptual fields: plan, status, provider IDs, billing cycle, period start/end, `cancelAtPeriodEnd`, trial end. Normalize internal states (`TRIAL`, `ACTIVE`, `PAST_DUE`, `CANCELLED`, `EXPIRED`, `SUSPENDED`, `INCOMPLETE`) from provider docs.

Failed subscription payment: webhook → internal status → grace policy → notify → retry → restrict features per policy. **Do not destroy tenant data** on first failure.

## Invoice integrity (tenant documents)

Backend validates and calculates subtotal, discount, tax, total, amount paid, balance. Angular is not authoritative.

Lifecycle: `DRAFT` editable; `SENT`/`PAID`/`CANCELLED` restricted — prefer credit notes / cancel-reissue over silent rewrite of finalized docs. Tax/GST fields only after verified legal requirements (do not claim GST compliance prematurely).

Quotation → invoice: single transaction, recalculated totals, `CONVERTED`, no duplicate conversion.

Invoice numbers: tenant-specific, unique, concurrency-safe (not naive `MAX+1`).

## Concurrency

Transactions, unique constraints, locking/idempotency keys for duplicate payment application, overpayment races, duplicate webhooks, concurrent refunds.

## Observability & admin

**Grafana/ops:** checkout rate, provider latency/failures, webhook receive/signature/process errors, pending age, success rate, refund errors — **no PII in metric labels**.

**Platform admin:** QuoteFlow subscription revenue, failed charges, refunds, MRR/ARR, gateway fees — not mixed with tenant invoice revenue.

**Tenant dashboard:** that tenant’s invoiced/collected/outstanding/overdue only.

**Ops reconciliation view:** unprocessed/failed webhooks, orphans, mismatches (internal only).

## Testing (when implemented)

Order creation; valid/invalid signatures; duplicate/out-of-order webhooks; success/fail/partial/manual/overpay; subscription activate/cancel; refunds if any; cross-tenant access; fake IDs; wrong amount/currency.

## Cost

Prefer provider transaction pricing; keep payment module inside the monolith until extraction is justified. No dedicated Payment microservice in MVP.

## Phase alignment

| Phase area | Focus |
|------------|--------|
| Quotation | Calculations, numbering, lifecycle |
| Quotation PDF | See [DOCUMENTS_ARCHITECTURE.md](DOCUMENTS_ARCHITECTURE.md) |
| Invoice | Lifecycle, conversion, numbering |
| Payment | Manual + partial + balance + receipt |
| Subscription | Entitlements |
| Razorpay | Platform billing + verified webhooks |
| Later | Tenant Pay Now, reconciliation jobs, refunds |

## Final principle

Correctness + security + idempotency + auditability + tenant isolation + reconciliation + performance + cost control.

Payment is not a frontend button. The DB business record is authoritative; the provider requires verification; the backend connects them.
