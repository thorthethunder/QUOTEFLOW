# ADR-005: Tenant Isolation Strategy

- **Status:** Accepted
- **Date:** 2026-09-13
- **Phase:** 2

## Context

QuoteFlow is multi-tenant. Business data must never leak across tenants. Browser-supplied identifiers are not a security boundary.

## Decision

- A **Business** row is the tenant.
- Each `app_users` row belongs to exactly one Business (`business_id` FK).
- Future APIs resolve trusted `userId` / `businessId` from the authenticated security context (Phase 3).
- Client-supplied `businessId` is never sufficient authorization.
- Tenant-owned repositories should prefer tenant-scoped queries; bare `findById` without a subsequent ownership check is an anti-pattern for tenant resources.

## Alternatives

1. Shared users across many businesses (membership table) — deferred; adds complexity before MVP need.  
2. Trust `businessId` from request body — rejected.

## Consequences

- Schema and docs establish isolation early.
- Full enforcement waits for Phase 3 authentication.
- Platform-admin global views remain a separate authorization domain.
