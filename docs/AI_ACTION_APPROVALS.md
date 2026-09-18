# AI Action Approvals (Phase 4)

> **Status: IMPLEMENTED** — controlled prepare → review → confirm → execute.  
> Production AI / AI actions: **NOT DEPLOYED** (`AI_ENABLED=false`, `AI_ACTIONS_ENABLED=false` by default).

## Principle

**MODEL PROPOSAL ≠ USER APPROVAL**

```text
AI proposes
  → backend validates
  → ActionProposal PENDING
  → user reviews exact payload
  → separate authenticated CONFIRM
  → re-authz + integrity + TOCTOU
  → existing QuotationService / InvoiceService
  → EXECUTED
```

The model cannot approve, confirm, or execute. There is no `confirm_action` tool.

## Action policy

### READ_ONLY
- customer_lookup, quotation_search, invoice_search, payment_status, business_summary

### ACTION_REQUIRES_APPROVAL (Phase 4–5)
- `quotation_create_draft` → `QUOTATION_CREATE_DRAFT`
- `invoice_create_draft` → `INVOICE_CREATE_DRAFT`
- `reminder_prepare` → `REMINDER_PREPARE` (prepare only — **does not send email**)
- `payment_reminder_send` → `PAYMENT_REMINDER_SEND` (human-approved queue via NotificationService)

See [AI_PAYMENT_REMINDERS.md](AI_PAYMENT_REMINDERS.md).

### FORBIDDEN
- payment.record / void, refund, subscription changes, sql.execute, repository.direct, shell, filesystem, arbitrary HTTP, reminder.send, …

Fail closed: unknown/unregistered/wrong-category → reject.

## Lifecycle

`PENDING` → `EXECUTED` | `CANCELLED` | `EXPIRED` | `FAILED`

## APIs

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/ai/actions/{id}` | Review (tenant + same-user) |
| POST | `/api/v1/ai/actions/{id}/confirm` | Human confirm (no payload body) |
| POST | `/api/v1/ai/actions/{id}/cancel` | Cancel pending |

Cross-tenant / cross-user → **404**.

## Security controls

1. Tenant + user binding on proposal  
2. SHA-256 integrity over actionType + businessId + userId + canonical payload  
3. TTL (`AI_ACTION_APPROVAL_TTL`, default 10m, hard max 1h)  
4. Pessimistic lock on confirm (one execution)  
5. Confirmation does **not** call the LLM  
6. Revalidation via existing business services (quota, active customer, etc.)  
7. Client cannot replace payload on confirm  

## Financial authority

Preview totals use `FinancialDocumentCalculator`. Execution uses existing create services. AI is not authoritative for money.

## Configuration

```text
AI_ACTIONS_ENABLED=false
AI_ACTION_APPROVAL_TTL=10m
AI_ACTION_RATE_USER=6
AI_ACTION_RATE_TENANT=20
```

## Reminder note

Phase 4 **accepts** prepared reminder text only (`reminder_prepare`).  
Phase 5 adds **human-approved sending** (`payment_reminder_send`) via NotificationService/outbox — see [AI_PAYMENT_REMINDERS.md](AI_PAYMENT_REMINDERS.md).

## Related
- [AI_PAYMENT_REMINDERS.md](AI_PAYMENT_REMINDERS.md)

- [BUSINESS_COPILOT.md](BUSINESS_COPILOT.md)
- [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md)
- [SECURITY.md](SECURITY.md)
