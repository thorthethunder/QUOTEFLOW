# QuoteFlow Incident Response Foundation

**Status:** Operational playbook (manual). Not automated incident response.  
**Phase:** 14

This document is a readiness foundation for production operators. It does not claim certification or guaranteed recovery times.

## Principles

1. **Contain** first (stop ongoing abuse).  
2. **Preserve evidence** (logs, timestamps, correlation IDs) before destructive cleanup.  
3. **Rotate** secrets that may be exposed.  
4. **Revoke** sessions / disable integrations as needed.  
5. **Assess** tenant impact honestly.  
6. **Restore** from backups only with verified integrity.  
7. **Communicate** internally; publish external notices only with legal/ops ownership.

## Suspected account compromise

- Force password change for affected user(s) when password-reset exists (future); until then disable user (`DISABLED`) and revoke refresh tokens for the user.  
- Review recent quotation/invoice/payment/notification activity for that business.  
- Rotate user-facing sessions (delete `refresh_tokens` rows for the user).  
- Residual: no MFA; access JWTs remain valid until short expiry.

## JWT signing secret compromise

- Treat as critical. Generate a new `JWT_SECRET` (≥32 bytes, not a known default).  
- Deploy with new secret (all outstanding access tokens fail verification).  
- Revoke all refresh tokens (truncate or mass-update `refresh_tokens.revoked_at`).  
- Require re-login for all users.  
- Audit auth failure spikes around the incident window.

## Database credential compromise

- Rotate DB password with the hosting provider.  
- Update `DATABASE_PASSWORD` / connection secret and restart apps.  
- Review DB audit/logs if available; look for unexpected DDL/DML.  
- Consider restore from known-good backup if integrity is uncertain.

## Razorpay key / webhook secret compromise

- Rotate Razorpay Key Secret and Webhook Secret in Razorpay dashboard.  
- Update env: `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`.  
- Temporarily set `BILLING_ENABLED=false` if abuse continues.  
- Reconcile `subscriptions` / `billing_transactions` against Razorpay for the window.  
- Do **not** treat frontend checkout completion as entitlement authority.

## Resend API key compromise

- Rotate Resend API key; update `RESEND_API_KEY`.  
- Set `EMAIL_PROVIDER=CONSOLE` or disable worker (`EMAIL_WORKER_ENABLED=false`) until rotated.  
- Review `notifications` rows created in the abuse window; mark suspicious PENDING rows CANCELLED if needed.  
- Check Resend dashboard for unexpected sends.

## Cross-tenant leakage suspicion

- Preserve logs with `correlationId`.  
- Reproduce with two test tenants if safe.  
- Patch authorization path; add regression to `TenantIsolationSecurityIT`.  
- Assess whether specific business IDs/resources were exposed; notify affected tenants per company policy.  
- Do not attempt to hide the incident.

## Payment inconsistency

- Prefer backend invoice + payment rows as source of truth.  
- Do not “fix” balances in UI.  
- Use void + corrective payment with audit notes when domain allows.  
- If concurrency bug suspected, freeze related invoice mutations until patch + regression test lands.

## Email abuse burst

- Lower `EMAIL_RATE_TENANT` / disable worker.  
- Identify tenant; consider suspending business.  
- Rotate Resend key if outbound volume indicates credential theft.  
- Review notification history for the tenant.

## Evidence checklist

- Application logs (include `correlationId`)  
- Time window (UTC)  
- Affected `business_id` / `user_id` (internal only)  
- Config change history  
- DB backup identifier used for recovery

## Security contact / disclosure (production requirement)

Before public launch, publish a real security contact (email or form) and a responsible disclosure process.  
Do **not** invent placeholder addresses in this repository.

## Related

- [SECURITY.md](SECURITY.md)  
- [OPERATIONS.md](OPERATIONS.md)  
- [SECURITY_TEST_MATRIX.md](SECURITY_TEST_MATRIX.md)  
- [ADR-019](adr/ADR-019-production-security-and-assurance-baseline.md)
