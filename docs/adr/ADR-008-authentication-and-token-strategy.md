# ADR-008: Authentication and Token Strategy

- **Status:** Accepted
- **Date:** 2026-09-13
- **Phase:** 3

## Context

QuoteFlow needs production-oriented authentication with multi-tenant isolation, revocable sessions, and low infrastructure cost.

## Decision

1. **Access token:** short-lived JWT (15 minutes), HS256, signed with server secret from configuration. Claims: `sub`, `businessId`, `tenantRole`, `iss`, `aud`, `iat`, `exp`, `jti`.
2. **Refresh token:** opaque high-entropy random value; persist **SHA-256 hash only**; 14-day TTL; rotate on every refresh with pessimistic DB lock.
3. **No access-token blacklist** initially — rely on short TTL + refresh revocation. Document grace window after suspension.
4. **Phase 3 transport:** return refresh token in JSON for API verification. **Phase 4:** prefer HttpOnly Secure SameSite cookie + CSRF.
5. **Trusted tenant** derives from verified JWT principal only (ADR-005).
6. **Password hashing:** BCrypt strength 12. Never store or log plaintext passwords.
7. **Platform roles** remain separate; tenant authorities are `ROLE_OWNER` / `ROLE_ADMIN` / `ROLE_STAFF` only.

## Alternatives

1. Long-lived JWT only — rejected (hard to revoke).  
2. Redis session store now — rejected (premature cost).  
3. BCrypt for refresh-token hashes — rejected (hurts lookup; tokens are random).  
4. Cookie transport in Phase 3 — deferred to Angular auth phase.

## Consequences

- PostgreSQL remains sufficient for refresh tokens.
- Stolen access tokens are time-bounded; stolen refresh tokens can be revoked/rotated.
- Key rotation incident response: rotate `JWT_SECRET`, revoke active refresh tokens (future admin tooling).
