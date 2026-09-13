# QuoteFlow Quotations

## Status

| Area | State |
|------|--------|
| Schema V4–V5 + API + Angular UI | **IMPLEMENTED** (Phase 6–7) |
| PDF generation | **IMPLEMENTED** (Phase 7) |
| Email / public acceptance | Quotation email **IMPLEMENTED** (Phase 13); public acceptance **FUTURE** |
| Invoice conversion | **IMPLEMENTED** (Phase 8) — see [INVOICES.md](INVOICES.md) |
| Free plan monthly quotation quota | **IMPLEMENTED** (Phase 11) — Business-TZ calendar month on `created_at` |

## Calculation policy (authoritative on server)

1. `lineSubtotal = round2(quantity × unitPrice)`
2. `subtotal = sum(lineSubtotals)`
3. Discount on subtotal: `NONE` | `PERCENTAGE` | `FIXED` (capped at subtotal)
4. `taxable = subtotal − discountAmount`
5. `taxAmount = round2(taxable × taxRate / 100)`
6. `totalAmount = taxable + taxAmount`

Rounding: **scale 2**, **HALF_UP**. Client preview is UX-only.

This is a **generic** tax model — not GST/VAT compliance.

## Numbering

Table `document_sequences` keyed by `(business_id, document_type)`.

Allocate with `SELECT … FOR UPDATE` (pessimistic lock), increment `next_value`, format `Q-000001`.

Unique per tenant: `(business_id, quotation_number)`. Tenants may share the same display number.

## Snapshots

**Customer** and **Business** snapshots are copied on create and DRAFT update. Send refreshes business snapshot once, then SENT freezes both. PDF uses snapshots only — see [DOCUMENTS.md](DOCUMENTS.md) / ADR-011 / ADR-012.

V5 backfill for pre-existing rows uses then-current Business profile (dev/pre-prod baseline only).

## Lifecycle

`DRAFT` → `SENT` (Mark as sent; no email)  
`DRAFT`|`SENT` → `CANCELLED`  
Only DRAFT is editable. Optimistic `@Version` → HTTP 409 on conflict.

Archived customers cannot be used for new/updated quotations. Existing quotations remain readable.

## PDF

`GET /api/v1/quotations/{id}/pdf` — authenticated, on-demand OpenPDF render. DRAFT/CANCELLED visibly marked.

## Expiration

`valid_until` is informational. No scheduler marks EXPIRED in Phase 6/7.

## API

`/api/v1/quotations` create/list/get/update + `/send` + `/send-email` + `/notifications` + `/cancel` + `/pdf` + `/convert-to-invoice`.

Detail responses may include `convertedInvoiceId` / `convertedInvoiceNumber` when a conversion exists (no `INVOICED` quotation status).

See [ADR-009](adr/ADR-009-monetary-calculation-and-rounding.md), [ADR-010](adr/ADR-010-tenant-document-numbering.md), [ADR-011](adr/ADR-011-historical-document-snapshot.md), [ADR-012](adr/ADR-012-pdf-rendering-strategy.md), [ADR-013](adr/ADR-013-quotation-to-invoice-conversion.md).
