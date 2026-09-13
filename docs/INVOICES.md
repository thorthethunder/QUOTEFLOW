# QuoteFlow Invoices

## Status

| Area | State |
|------|--------|
| Schema V6 + API + Angular UI | **IMPLEMENTED** (Phase 8) |
| Quotation → Invoice conversion | **IMPLEMENTED** (Phase 8) |
| Invoice PDF | **DEFERRED** (reuse quotation PDF helpers later) |
| Payments / receipts / balance | **IMPLEMENTED** (Phase 9) — see [PAYMENTS.md](PAYMENTS.md) |
| Dashboard invoiced / outstanding | **IMPLEMENTED** (Phase 10) — SENT by `issue_date`; see [REPORTING.md](REPORTING.md) |
| Free plan monthly invoice quota | **IMPLEMENTED** (Phase 11) — standalone + conversion share one lock/quota |
| Email / public invoice page / gateways | Payment reminder email **IMPLEMENTED** (Phase 13); public page / gateways **FUTURE** |

Generic invoice functionality only — **not** GST/VAT/e-invoicing/accounting compliance.

## Core principle

An Invoice is a **separate historical financial document**. After conversion, changing the quotation must never silently change the invoice (and vice versa).

```text
Quotation
  ↓ explicit conversion (SENT only)
Invoice
  ├── own UUID + INV-###### number
  ├── copied items + money inputs
  ├── authoritative recalculated totals
  ├── copied customer + business snapshots
  ├── source_quotation_id (provenance only)
  └── independent lifecycle (DRAFT / SENT / CANCELLED)
```

## Schema (V6)

- `invoices` — tenant-owned; unique `(business_id, invoice_number)`; unique `source_quotation_id` (NULLs allowed for standalone)
- `invoice_items` — owned lines; `ON DELETE CASCADE` from invoice
- FK deletes: Business/Customer/Quotation → Invoice = **RESTRICT**
- `document_sequences` CHECK extended: `QUOTATION` | `INVOICE`

## Numbering

Separate per-tenant `INVOICE` sequence via `document_sequences` + `SELECT … FOR UPDATE`.

Format: `INV-000001`. Not shared with quotation stream.

Frontend cannot set invoice number. Gaps may occur if a transaction rolls back after allocation (not promised gapless).

## Calculation

Shared `FinancialDocumentCalculator` (ADR-009). `QuotationCalculator` remains a thin facade.

Canonical example (must match quotation and converted invoice):

| Component | Amount |
|-----------|--------|
| Subtotal | 250.00 |
| Discount (10%) | 25.00 |
| Tax (18%) | 40.50 |
| **Total** | **265.50** |

Client totals are ignored. Scale 2, HALF_UP.

## Snapshots

| Path | Customer snapshot | Business snapshot |
|------|-------------------|-------------------|
| Convert from quotation | Copy quotation’s historical snapshot | Copy quotation’s historical snapshot |
| Standalone create | Snapshot current Customer | Snapshot current Business |
| DRAFT update | Re-snapshot current Customer/Business | Same |
| After SENT | Immutable | Immutable |

Conversion uses quotation snapshots even if the live Customer was renamed or archived.

## Conversion

- Eligibility: **SENT** quotations only (DRAFT / CANCELLED rejected with 409).
- One quotation → at most one invoice (`UNIQUE(source_quotation_id)`).
- Transaction: allocate number → copy snapshots/items/inputs → recalculate → assert totals match quotation → persist.
- Duplicate / concurrent convert → HTTP **409** `QUOTATION_ALREADY_INVOICED` (+ `details.existingInvoiceId` when tenant-safe).
- Converted invoice starts as **DRAFT** (notes/terms copied; editable before Mark as Sent).
- Quotation status is **not** changed to INVOICED; association exposed as `convertedInvoiceId` / `convertedInvoiceNumber`.

Archived customer: **blocks** standalone invoice create; **allows** conversion from a SENT quotation.

## Lifecycle

`DRAFT` → `SENT` (`POST …/send` — Mark as Sent; no email)  
`DRAFT`|`SENT` → `CANCELLED` (`POST …/cancel`)

Only DRAFT is editable. Optimistic `@Version` → 409 on stale edit.

**Document status ≠ payment status.** Phase 9 will track payments separately (`amountPaid` / `balanceDue`) without conflating PAID into document lifecycle here.

## API

| Method | Path |
|--------|------|
| POST | `/api/v1/invoices` |
| GET | `/api/v1/invoices` (`q`, `status`, page/size/sort) |
| GET | `/api/v1/invoices/{id}` |
| PUT | `/api/v1/invoices/{id}` |
| POST | `/api/v1/invoices/{id}/send` |
| POST | `/api/v1/invoices/{id}/cancel` |
| POST | `/api/v1/quotations/{id}/convert-to-invoice` |

No `businessId` in routes/DTOs. Cross-tenant IDs → 404.

Sort allowlist: `invoiceNumber`, `issueDate`, `dueDate`, `createdAt`, `updatedAt`, `totalAmount`.

## Angular

- `/app/invoices`, `/new`, `/:id`, `/:id/edit`
- Quotation detail: Convert to Invoice / View Invoice
- Desktop table / mobile cards; Mark as Sent wording

## Future (not Phase 8)

- Payment recording, balance, receipts
- Invoice PDF (reuse snapshot + document helpers)
- Email delivery, public pages, gateways, recurring, credit notes

See [ADR-013](adr/ADR-013-quotation-to-invoice-conversion.md), [ADR-009](adr/ADR-009-monetary-calculation-and-rounding.md), [PAYMENTS_ARCHITECTURE.md](PAYMENTS_ARCHITECTURE.md).
