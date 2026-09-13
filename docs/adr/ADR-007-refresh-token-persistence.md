# ADR-007: Refresh Token Persistence Strategy

- **Status:** Accepted
- **Date:** 2026-09-13
- **Phase:** 2 (schema only; issuance in Phase 3)

## Context

Refresh tokens are high-value credentials. Persisting raw tokens increases breach impact.

## Decision

- Phase 3 will issue a random **opaque** refresh token to the client.
- Database stores only a **non-reversible hash** in `refresh_tokens.token_hash` (unique).
- Columns support `expires_at`, nullable `revoked_at`, `created_at`, optional `last_used_at`.
- No raw `token` column. No Redis/token store in Phase 2.

## Alternatives

1. Store raw tokens in PostgreSQL — rejected.  
2. JWT-only refresh without server revocation list — rejected for MVP revocation needs.  
3. Redis session store now — rejected (premature cost/complexity).

## Consequences

- Schema is ready for Phase 3 auth.
- Stolen DB hashes are not immediately reusable as bearer tokens.
