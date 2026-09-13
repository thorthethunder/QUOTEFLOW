# QuoteFlow Customers

## Status

| Area | State |
|------|--------|
| Schema V3 + API + Angular UI | **IMPLEMENTED** (Phase 5) |
| Free plan active-customer limit | **IMPLEMENTED** (Phase 11) — server-enforced; ARCHIVED does not count |
| Quotations linked to customers | **IMPLEMENTED** (Phase 6; customer snapshot on quotation) |
| Invoices linked to customers | **IMPLEMENTED** (Phase 8) |

## Tenant ownership

Every customer belongs to exactly one `business_id`.

Trusted tenant comes from `AuthenticatedUser.businessId` (JWT). Clients never supply tenant IDs. Cross-tenant UUID access returns **404** (non-enumerating).

## Schema (`customers`)

See [DATABASE.md](DATABASE.md). Soft lifecycle: `ACTIVE` | `ARCHIVED` (no hard delete in Phase 5).

## API

| Method | Path | Notes |
|--------|------|--------|
| POST | `/api/v1/customers` | Create (OWNER/ADMIN/STAFF) |
| GET | `/api/v1/customers` | List; default status `ACTIVE`; `q`, `status`, `page`, `size`, `sort` |
| GET | `/api/v1/customers/{id}` | Tenant-scoped |
| PUT | `/api/v1/customers/{id}` | Update permitted fields only |
| POST | `/api/v1/customers/{id}/archive` | Soft archive |

Pagination: page 0-based, default size **20**, max **100**. Sort allowlist: `displayName`, `createdAt`, `updatedAt`.

## Authorization

Authenticated tenant roles `OWNER`, `ADMIN`, and `STAFF` may manage customers. No platform-admin customer access.

## Privacy

Customer PII (name, email, phone, address, tax ID, notes) stays tenant-scoped. Logs emit customerId/businessId only — not notes/email/phone.

## Future documents

Quotations snapshot customer display fields (Phase 6). Invoices should do the same when implemented. See [QUOTATIONS.md](QUOTATIONS.md) / ADR-011.
