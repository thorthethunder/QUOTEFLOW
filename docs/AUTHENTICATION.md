# QuoteFlow Authentication

## Status

| Area | State |
|------|--------|
| Register / login / refresh / logout | **IMPLEMENTED** (Phase 3) |
| GET `/api/v1/me` | **IMPLEMENTED** |
| Angular auth UI | **IMPLEMENTED** (Phase 4) |
| HttpOnly refresh cookie + CSRF | **IMPLEMENTED** (Phase 4) |
| Password reset / email verification / MFA | **FUTURE** |
| Correlation ID / API no-store / security headers | **IMPLEMENTED** (Phase 14) |

## Endpoints

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/auth/register` | Public; sets refresh cookie |
| POST | `/api/v1/auth/login` | Public; sets refresh cookie |
| POST | `/api/v1/auth/refresh` | Cookie (or body); **CSRF required**; rotates cookie |
| POST | `/api/v1/auth/logout` | Cookie (or body); **CSRF required**; clears cookie |
| GET | `/api/v1/auth/csrf` | Public; ensures `XSRF-TOKEN` cookie |
| GET | `/api/v1/me` | Bearer access JWT |

## Registration / login

Unchanged from Phase 3. Browser responses omit `refreshToken` from JSON (`return-in-body=false`). Tests may set `REFRESH_TOKEN_RETURN_IN_BODY=true`.

## Access JWT

| Setting | Value |
|---------|--------|
| Algorithm | HS256 |
| Lifetime | **15 minutes** |
| Frontend storage | **Memory only** (not localStorage) |

## Refresh tokens (Phase 4 browser)

| Setting | Value |
|---------|--------|
| Cookie name | `qf_refresh` |
| Flags | HttpOnly; Secure in prod; SameSite=Lax; Path=`/api/v1/auth` |
| Hashing | SHA-256 in DB |
| Lifetime | 14 days |
| Rotation | Every refresh |

Same-origin deployment (Angular `/api` proxy in dev; reverse proxy in prod) so Lax cookies are sent.

## CSRF

CSRF protection applies **only** to `POST /auth/refresh` and `POST /auth/logout` (cookie-authenticated state changes).

- Cookie: `XSRF-TOKEN` (readable by JS)
- Header: `X-XSRF-TOKEN` (Angular `withXsrfConfiguration`)
- Login/register and Bearer APIs do not require CSRF

## Frontend flow

```text
App start → GET /auth/csrf → POST /auth/refresh (cookie) → GET /me
API 401 → single-flight refresh → retry once
Logout → POST /auth/logout (+ CSRF) → clear memory → login
```

## Residual risks

- No MFA / password reset / email verification
- Access JWT grace window after suspension
- SameSite=Lax assumes same-site hosting; cross-site SPAs need SameSite=None + Secure + stricter CSRF review
- If logout request never reaches the server, cookie may remain until expiry (local state still cleared)

See [ADR-008](adr/ADR-008-authentication-and-token-strategy.md), [FRONTEND_ARCHITECTURE.md](FRONTEND_ARCHITECTURE.md).
