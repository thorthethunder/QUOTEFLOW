# ADR-019: Production Security and Assurance Baseline

- **Status:** Accepted  
- **Date:** 2026-09-13  
- **Phase:** 14  

## Context

Phases 1–13 delivered QuoteFlow’s product surface. Before Docker/production deployment (Phase 15), QuoteFlow needs an explicit security and assurance baseline: defense in depth, least privilege, tenant isolation, session safety, financial/concurrency correctness, privacy-conscious logging, dependency hygiene, and production fail-closed configuration.

Claims of “unhackable”, “zero vulnerabilities”, or formal certifications are out of scope and prohibited.

## Decision

1. **Practical OWASP ASVS Level 2 baseline** where applicable, plus OWASP Top 10 principles.  
2. **Tenant isolation** remains a highest-risk boundary: every tenant resource is authorized via JWT `businessId`, typically returning **404** for cross-tenant access.  
3. **Auth/session model** unchanged in spirit: short-lived access JWT (memory-only on Angular), long-lived hashed refresh tokens, rotation, CSRF on cookie refresh/logout, explicit CORS.  
4. **Production fail-closed** for JWT secret, CORS origins, and database credentials (`ProductionSecurityValidator` + `application-prod.yml`).  
5. **Security headers**: nosniff, framing deny / CSP `frame-ancestors 'none'`, Referrer-Policy, Permissions-Policy; HSTS only on `prod`. SPA CSP documented in `index.html` (Razorpay checkout origins allowlisted; Material requires style `unsafe-inline` for now).  
6. **Sensitive API cache**: `Cache-Control: private, no-store` for `/api/v1/**`.  
7. **Correlation IDs** for supportability, not authorization.  
8. **Rate limits** remain in-process (per instance); Redis is explicitly deferred.  
9. **Dependency assurance**: lockfiles, `npm ci` in CI, `npm audit` review; Maven CVE scan may be incomplete if advisory DB unavailable.  
10. **Incident readiness**: `INCIDENT_RESPONSE.md` + backup/restore targets in OPERATIONS.  
11. **Future AI trust boundary**: AI tools must call Spring business services (authz, validation, transactions)—never repositories directly; high-impact actions require human approval.  
12. **External provider E2E** (Razorpay Test Mode, Resend domain) remain deferred release gates—not Phase 14 blockers.

## Consequences

- Security work continues as regression suites + documented residual risks, not theater.  
- Phase 15 may deploy with this baseline, still requiring real provider validation later.  
- CSP may need nonce-based style hardening in a later pass.

## References

- [SECURITY.md](../SECURITY.md)  
- [SECURITY_TEST_MATRIX.md](../SECURITY_TEST_MATRIX.md)  
- [INCIDENT_RESPONSE.md](../INCIDENT_RESPONSE.md)  
- [ADR-008](ADR-008-authentication-and-token-strategy.md)  
- [ADR-018](ADR-018-transactional-email-and-notification-outbox.md)
