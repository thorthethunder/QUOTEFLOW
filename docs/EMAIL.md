# QuoteFlow Email

**Phase 13** — transactional email for quotations and payment reminders.

## Provider selection

| Provider | When |
|----------|------|
| **CONSOLE** | Default local/dev — logs safe metadata, never sends |
| **FAKE** | Automated tests |
| **RESEND** | Production path — API-based transactional email |

Production (`prod` profile) **forbids** `CONSOLE`/`FAKE` unless `EMAIL_ALLOW_DEV_PROVIDER=true` (isolated staging only). `RESEND` requires `RESEND_API_KEY` and `EMAIL_FROM`.

Rationale for Resend: transactional focus, simple HTTP API, idempotency headers, no multi-SDK sprawl. Domain verification (SPF/DKIM/DMARC) is required before claiming deliverability.

## Configuration

```text
EMAIL_PROVIDER=CONSOLE|FAKE|RESEND
EMAIL_FROM=noreply@yourdomain
EMAIL_FROM_NAME=QuoteFlow
RESEND_API_KEY=...   # only when EMAIL_PROVIDER=RESEND
EMAIL_WORKER_ENABLED=true
EMAIL_CLAIM_LEASE=10m          # stale SENDING reclaim after worker crash
EMAIL_ALLOW_DEV_PROVIDER=false # staging override only
```

Core app health does **not** depend on email provider availability.

## From / Reply-To

- **From:** configured platform address (verified domain in production)
- **Reply-To:** business snapshot email when valid; otherwise omitted

Do not spoof tenant domains as From without provider domain verification.

## Recipient policy

Quotation / reminder recipients come from **document customer email snapshot** (server-loaded, tenant-scoped). Angular cannot supply arbitrary recipient authority.

## Templates

Server-rendered HTML + plain text. Tenant/customer text is HTML-escaped. Subjects are CRLF-sanitized.

## Branding

FREE: QuoteFlow footer allowed. PRO/BUSINESS: branding follows entitlement (`showQuoteFlowBranding`) consistent with PDF policy.

## Attachments

Quotation emails attach backend-generated PDF (size-capped). No user-upload attachments. Invoice PDF not in scope.

## APIs

- `POST /api/v1/quotations/{id}/send-email`
- `GET /api/v1/quotations/{id}/notifications`
- `POST /api/v1/invoices/{id}/send-reminder`
- `GET /api/v1/invoices/{id}/notifications`
- `GET /api/v1/notifications/{id}`

See [NOTIFICATIONS.md](NOTIFICATIONS.md).
