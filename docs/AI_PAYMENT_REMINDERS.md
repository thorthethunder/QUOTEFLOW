# AI Payment Reminders (Phase 5)

> **Status: IMPLEMENTED** — human-approved payment reminder sending via NotificationService outbox.  
> Production AI / AI actions / Resend: **NOT DEPLOYED** by default.

## Principle

**AI writes. QuoteFlow verifies. Human approves. NotificationService sends.**

```text
User asks to send reminder
  → read-only invoice/payment lookup
  → payment_reminder_send PREPARE
  → server resolves recipient from invoice customer email
  → AiActionProposal PENDING (integrity + TTL + tenant/user binding)
  → Angular review (customer, recipient, invoice, balance, subject, body)
  → POST /api/v1/ai/actions/{id}/confirm
  → revalidate payment state + recipient + entitlement
  → InvoiceReminderService → NotificationService → durable outbox
  → EmailProvider (FAKE / CONSOLE / RESEND)
```

## Action policy

| Tool | Category | Effect |
|------|----------|--------|
| `reminder_prepare` | ACTION_REQUIRES_APPROVAL | Accept prepared text only — **no email** |
| `payment_reminder_send` | ACTION_REQUIRES_APPROVAL | Human-approved **queue** of reminder email |

Forbidden: `email.send_anything`, `email.send_to_address`, `reminder_send`, `confirm_action`, bulk/marketing email.

## Recipient authority

Recipient comes from **invoice snapshot customer email**, never from model arguments.

- Model cannot override to `attacker@example.com`
- Missing/invalid email → no executable send proposal
- Recipient change after prepare → confirm rejected (`AI_ACTION_STALE_RECIPIENT`); new review required

## Payment / balance authority

`PaymentSummaryCalculator` / payment services are authoritative.

- PAID / zero balance → cannot prepare or confirm send
- Balance change after prepare → confirm rejected (`AI_ACTION_STALE_BALANCE`)
- Reviewed amount must still match current outstanding at confirm

## Content rules

- Plain text body; subject CRLF-stripped
- AI body escaped into controlled email template (`invoice_reminder_standard`)
- Unsupported claims (legal threats, penalties, credit reporting) rejected at prepare
- No arbitrary HTML from the model

## Idempotency

Notification idempotency key: `AI_ACTION:{proposalId}`

Phase 4 one-time proposal execution + unique idempotency prevents double-click / concurrent / HTTP-retry duplicate **local enqueue**.

## Delivery semantics

| Layer | Semantics |
|-------|-----------|
| Local outbox enqueue | Exactly-once per approved proposal (idempotent) |
| External provider | **At-least-once** — provider may accept then crash before local ack; retries can duplicate externally |

Do **not** claim exactly-once email delivery.

## Entitlement

Uses existing `EMAIL_SENDING` (Pro/Business). Re-checked at prepare and confirm.

## APIs

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/v1/ai/actions/payment-reminders/prepare` | Manual / invoice-UI prepare (no LLM required) |
| GET | `/api/v1/ai/actions/{id}` | Review |
| POST | `/api/v1/ai/actions/{id}/confirm` | Approve & queue |
| POST | `/api/v1/ai/actions/{id}/cancel` | Cancel |

## UX wording

- Before approval: “ready for review”
- After confirm: “queued for delivery” (not “delivered” unless provider status is known)

## Related

- [AI_ACTION_APPROVALS.md](AI_ACTION_APPROVALS.md)
- [EMAIL.md](EMAIL.md)
- [NOTIFICATIONS.md](NOTIFICATIONS.md)
- [BUSINESS_COPILOT.md](BUSINESS_COPILOT.md)
