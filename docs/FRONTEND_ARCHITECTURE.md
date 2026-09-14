# QuoteFlow Frontend Architecture

## Status

| Area | State |
|------|--------|
| Public landing | **IMPLEMENTED** |
| Login / Register | **IMPLEMENTED** (Phase 4) |
| Auth shell + tenant dashboard | **IMPLEMENTED** (Phase 4 shell; Phase 10 real overview) |
| Plan & usage UI | **IMPLEMENTED** (Phase 11 + 12) — `/app/plan`; checkout via lazy Razorpay script |
| Quotation / invoice email UX | **IMPLEMENTED** (Phase 13) — send dialog, reminder dialog, history |
| Customer / quote / invoice / payment UI | **IMPLEMENTED** |

## Structure

```text
src/app/
  core/auth/          AuthService, interceptor, guards, models
  layout/app-shell/   Authenticated toolbar + sidenav
  features/
    landing/
    auth/login|register/
    dashboard/        Business overview + DashboardApiService
    customers/        list, form, detail + CustomerApiService
    quotations/       list, editor, detail + QuotationApiService (+ convert to invoice)
    invoices/         list, editor, detail + InvoiceApiService (+ payment summary/history)
    payments/         PaymentApiService + record/void dialogs
    plan/             PlanApiService, BillingApiService, EntitlementStore, RazorpayCheckoutLoader, Plan & usage page
    notifications/    NotificationApiService + models (quotation email / invoice reminder)
  environments/
```

Standalone components, lazy routes, signals for auth status.

**Auth (Phase 14 affirmations):** access JWT is memory-only (never localStorage/sessionStorage); refresh is HttpOnly cookie; Angular route guards are UX only — backend remains authoritative. SPA CSP is set in `index.html` (Razorpay checkout origins allowlisted; Material currently needs `style-src 'unsafe-inline'`). Login `returnUrl` is restricted to same-app relative paths via `safeReturnUrl` (rejects `//…` and absolute URLs).

**API base URL (Phase 15/16):** production default `apiBaseUrl: '/api/v1'`. Hosted staging loads public `/config.json` at runtime (`loadPublicAppConfig`) so Cloudflare Pages can change the API target without rebuilding TypeScript. **Staging topology:** same-origin `/api` via Pages Function proxy to Railway (see [DEPLOYMENT.md](DEPLOYMENT.md) / ADR-021).

Customer / Quotation list UI: table ≥768px, card list on smaller viewports; server-side search (debounced) + Material paginator. Quotation editor uses stacked line-item cards on mobile; live totals are preview-only — backend save is authoritative.

## Design tokens

Defined in `src/styles.scss` as CSS custom properties (`--qf-*`):

- spacing (`--qf-space-1` … `--qf-space-8`)
- radii, content max width
- breakpoints: mobile &lt;600, tablet 600–959, desktop ≥960
- surfaces, muted text, elevation

Angular Material 3 theme via `mat.theme()` (azure primary). Tokens are light-first and can later gain a dark override without a second UI framework.

## Responsive strategy

One layout system (Grid/Flex + Material sidenav modes):

- Mobile: overlay drawer, stacked auth forms
- Tablet: overlay drawer
- Desktop: persistent sidenav; auth split brand | form
- Large desktop: capped content width (`--qf-content-max`)

Do not add Bootstrap/Tailwind.

## Auth state

`INITIALIZING` → `AUTHENTICATED` | `UNAUTHENTICATED`

Access JWT: **in-memory only**.  
Refresh: **HttpOnly cookie** (`qf_refresh`) via same-origin `/api` proxy in development.  
CSRF: cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN` for refresh/logout.

## Browser expectations

Primary verification: Chromium/Chrome. Safari/Firefox/iOS/Android device matrices are FUTURE.

See [AUTHENTICATION.md](AUTHENTICATION.md) and [SECURITY.md](SECURITY.md).
