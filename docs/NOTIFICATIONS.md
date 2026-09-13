# QuoteFlow Notifications

**Phase 13** — durable transactional email outbox for tenant business communication.

## Principle

Business records are authoritative. Email is a secondary side effect.

```text
Create/update quotation or invoice  →  commits successfully
Optional send-email / send-reminder →  creates notification row
Outbox worker                       →  calls EmailProvider
Provider failure                    →  notification FAILED/retry; document stays SENT
```

## Architecture

- `Notification` entity + `notifications` table (V10)
- `NotificationService.enqueue` — tenant-scoped, dedupes in-flight PENDING/SENDING
- `NotificationDeliveryService` + `NotificationOutboxWorker` — claim with `FOR UPDATE SKIP LOCKED` / `claimOne`
- **Crash recovery (Phase 15):** stale `SENDING` rows older than `EMAIL_CLAIM_LEASE` (default 10m, based on `updated_at`) are reclaimable so a worker death cannot leave permanent stuck sends
- `EmailProvider` — CONSOLE (default), FAKE (tests), RESEND (optional)

## Status terminology

| Status | Meaning |
|--------|---------|
| PENDING | Queued locally (including scheduled retries) |
| SENDING | Claimed by a worker |
| SENT | Provider **accepted** the API request (not inbox delivery) |
| FAILED | Permanent failure after retries exhausted |
| CANCELLED | Reserved |

`DELIVERED` is not used until provider webhooks confirm delivery.

## Types (tenant business)

- `QUOTATION_EMAIL` — PDF attached
- `INVOICE_REMINDER` — no invoice PDF (not implemented)
- `RECEIPT_EMAIL` — foundation type only
- `PLATFORM_NOTICE` — reserved; not used in Phase 13 UX

## Entitlement

`EMAIL_SENDING` via `EntitlementService.requireFeature`:

- FREE: denied (`FEATURE_NOT_AVAILABLE`)
- PRO / BUSINESS: allowed

## Idempotency / RETRY / RESEND

| Situation | Behavior |
|-----------|----------|
| Accidental/concurrent while **PENDING** or **SENDING** | Reuse same notification; no second provider send |
| Same idempotency key while **SENT** (no `resend`) | Reuse existing; no duplicate communication |
| Same idempotency key while **FAILED** (no `resend`) | **RETRY** — requeue the same row (`PENDING`, attempts reset) |
| Request with `resend: true` (no in-flight) | **RESEND** — create a **new** notification; history kept |

`uq_notifications_active_reference` only covers PENDING/SENDING, so FAILED/SENT never permanently block recovery.

Concurrent enqueue races (unique index on active reference or idempotency key) resolve by returning the winning row via a fresh transaction lookup — never a second in-flight send.

**RETRY** = continue the same notification after failure (same row, attempts reset, `resend: false`).  
**RESEND** = deliberate new communication after an earlier completed send (`resend: true`, new row; history preserved).

## Outbox retry (provider transient failures)

Transient failures: backoff 1m → 5m → 30m, max attempts configurable (default 4). Permanent errors fail immediately.

Ambiguous provider accept after timeout: residual duplicate-send risk documented; Resend Idempotency-Key header used when configured.

## AI readiness (future)

AI may draft reminder text → **user approval** → `NotificationService` → provider. AI never sends directly.

See [EMAIL.md](EMAIL.md), [ADR-018](adr/ADR-018-transactional-email-and-notification-outbox.md).
