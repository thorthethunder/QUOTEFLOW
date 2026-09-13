# QuoteFlow Platform Billing (Razorpay)

**Phase 12** — QuoteFlow SaaS subscription checkout, webhooks, and entitlement sync.

## Hard separation

| Domain | Money flow | Tables |
|--------|------------|--------|
| **Platform billing** | Tenant → QuoteFlow | `subscriptions`, `billing_webhook_events`, `billing_transactions` |
| **Tenant payments** | Customer → Tenant | `payments`, invoices, receipts |

Never store SaaS charges in `payments`.

## Feature flag

`BILLING_ENABLED=false` (default): FREE product works; checkout returns `BILLING_NOT_AVAILABLE`.

`BILLING_ENABLED=true` requires Razorpay config. Production forbids `BILLING_PROVIDER=FAKE` when billing is enabled (`FAKE` is tests-only).

## Provider abstraction

`BillingProvider` (+ `RazorpayBillingProvider` / `FakeBillingProvider`).

Core entitlement code never imports Razorpay SDK types.

## Plan mapping (server only)

Angular sends `{ plan, billingInterval }`. Backend maps to env plan IDs:

| QuoteFlow | Interval | Env |
|-----------|----------|-----|
| PRO | MONTHLY | `RAZORPAY_PRO_MONTHLY_PLAN_ID` |
| PRO | ANNUAL | `RAZORPAY_PRO_ANNUAL_PLAN_ID` |
| BUSINESS | MONTHLY | `RAZORPAY_BUSINESS_MONTHLY_PLAN_ID` |

Prices remain in `PlanCatalog` (₹199/mo, ₹1,999/yr, ₹499/mo). Operators must create matching Razorpay Plans manually.

## Checkout flow

1. OWNER `POST /api/v1/billing/checkout`
2. Backend creates/reuses provider subscription (row lock)
3. Angular opens Razorpay Checkout (public `keyId` only)
4. `POST /api/v1/billing/checkout/verify` — HMAC of `payment_id|subscription_id`
5. Provider fetch + local apply
6. Webhooks remain durable authority

## Activation policy

Paid entitlements when provider status ∈ `authenticated|active|pending|halted|paused|resumed`.

- `pending`/`halted` → internal `PAST_DUE` (keep paid plan; data retained)
- `cancelled|completed|expired` → FREE (prospective limits only)

## Cancellation

`POST /api/v1/billing/subscription/cancel` (OWNER) → Razorpay `cancel_at_cycle_end=true`.

Paid-to-paid changes deferred.

## Webhooks

`POST /api/v1/webhooks/razorpay` — JWT not required; `X-Razorpay-Signature` HMAC over **raw body**.

Idempotency: `X-Razorpay-Event-Id` (+ unique constraint).

## Manual Razorpay setup (operator)

1. Enable Subscriptions in Razorpay Test Mode  
2. Create Plans matching QuoteFlow prices  
3. Copy plan IDs into env  
4. Create API keys + **separate** webhook secret  
5. Webhook URL: `https://<api>/api/v1/webhooks/razorpay`  
6. Subscribe: `subscription.authenticated`, `activated`, `charged`, `pending`, `halted`, `cancelled`, `completed`  
7. Set `BILLING_ENABLED=true`  
8. Run a Test Mode checkout  

Never put Test secrets in Live.

## Local webhook testing

Use signed fixtures in integration tests. Do not disable signature checks for localhost.

## Security notes

- Secrets never returned to Angular  
- OWNER-only checkout/cancel  
- CSRF unchanged (webhook authenticated by signature)  
- Bounded HTTP timeouts; no write retries  
- CSP (if deployed): allow script from `https://checkout.razorpay.com` only on Plan/billing routes; do not loosen site-wide  

See [ADR-017](adr/ADR-017-platform-billing-and-razorpay-subscriptions.md).
