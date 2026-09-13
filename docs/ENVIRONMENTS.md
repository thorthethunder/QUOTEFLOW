# QuoteFlow environments

| | LOCAL | PROD_LOCAL | STAGING | PRODUCTION |
|--|-------|------------|---------|------------|
| **Purpose** | Developer inner loop | Docker production-like smoke | Hosted pre-production | Final release (future) |
| **Frontend** | `ng serve` :4200 + proxy | nginx compose :8088 | Cloudflare Pages | Cloudflare Pages |
| **Backend** | `spring-boot:run` | container :8089 | Railway container | Railway container |
| **Database** | Compose Postgres :5432 | Compose volume :5433 | Managed Postgres (private) | Managed Postgres (private) |
| **Profile** | default/dev | `prod` + staging allow flags | `prod,staging` | `prod` |
| **Billing** | usually off / FAKE tests | off | **off** (`BILLING_ENABLED=false`) | off until release gates |
| **Email** | CONSOLE | CONSOLE + `EMAIL_ALLOW_DEV_PROVIDER` | **DISABLED** (or Resend if validating) | RESEND when verified |
| **AI** | none | none | none | future backend-only |
| **CORS** | localhost:4200 | localhost:8088 | exact staging frontend origin | exact production origin |
| **Cookies** | Secure=false | Secure=true behind proxy | Secure=true | Secure=true |
| **Logging** | INFO/DEBUG local | INFO | INFO | INFO |
| **Data** | disposable | disposable | synthetic only | real tenants |

## Staging hostname preference

Prefer same registrable domain:

- `https://staging.example.com` (Pages)
- `https://api-staging.example.com` (Railway)

If using `*.pages.dev` + `*.up.railway.app` **without** a same-origin `/api` proxy, refresh cookies are **cross-site** and will fail under SameSite=Lax. That topology is a **Phase 16 blocker** unless fixed via proxy or custom domain — do not “fix” with SameSite=None casually.

## Secrets

Never share staging and production JWT/DB/provider secrets. Rotate independently.
