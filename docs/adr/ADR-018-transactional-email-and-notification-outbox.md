# ADR-018: Transactional Email and Notification Outbox Strategy

- **Status:** Accepted
- **Date:** 2026-09-13
- **Phase:** 13

## Context

QuoteFlow must email quotations and payment reminders without coupling business transactions to SMTP/API success, without Kafka/Redis, and without trusting Angular for recipients or HTML.

## Decision

1. **DB outbox** (`notifications`) — durable queue with status, attempts, next_attempt_at.  
2. **`EmailProvider` abstraction** — CONSOLE/FAKE/RESEND; no SDK types in core domain.  
3. **Async worker** — Spring `@Scheduled` + PostgreSQL `SKIP LOCKED` / conditional claim.  
4. **Separate transactions** — mark document SENT + enqueue → commit → deliver.  
5. **SENT = provider accepted**, not inbox delivered.  
6. **Feature gate** `EMAIL_SENDING` on PRO/BUSINESS.  
7. **RETRY vs RESEND** — FAILED requeues same row; explicit `resend` creates a new row.  
8. **AI later:** draft → human approve → NotificationService; never autonomous send.

## Alternatives rejected

- Send email inside quotation create transaction — rolls back business on outage.  
- Kafka/Redis queues — cost/complexity for current scale.  
- Angular-rendered email HTML — XSS and branding bypass risk.

## Consequences

- Operators must verify sending domain for Resend/production.  
- Residual duplicate risk on ambiguous provider timeouts mitigated by idempotency keys where supported.  
- Invoice PDF email deferred until invoice PDF exists.

## References

- [NOTIFICATIONS.md](../NOTIFICATIONS.md)  
- [EMAIL.md](../EMAIL.md)
