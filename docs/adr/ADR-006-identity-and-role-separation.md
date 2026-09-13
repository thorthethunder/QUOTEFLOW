# ADR-006: Identity and Role Separation

- **Status:** Accepted
- **Date:** 2026-09-13
- **Phase:** 2

## Context

Login must identify a single account. Tenant “ADMIN” must not imply QuoteFlow platform power. SQL `user` is a reserved/confusing name.

## Decision

- Table name: `app_users` (not `user`).
- Login email is **globally unique** after trim+lowercase normalization.
- Password material is stored only as `password_hash` (no plaintext column). BCrypt (strength 12) in Phase 3.
- Tenant roles: `OWNER`, `ADMIN`, `STAFF` — constrained in DB; not ordinals.
- Platform roles (`PLATFORM_*`) are a **separate** future authorization domain; not values of `tenant_role`.

## Alternatives

1. Per-tenant email uniqueness — rejected for MVP: login would require tenant selection.  
2. Encode platform admin as tenant role `ADMIN` — rejected: privilege confusion.

## Consequences

- Clear login path and role vocabulary.
- Platform-admin implementation remains FUTURE (ADMIN phases).
