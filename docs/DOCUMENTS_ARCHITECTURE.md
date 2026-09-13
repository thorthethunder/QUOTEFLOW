# QuoteFlow Documents & PDF Architecture (FUTURE)

> **Status: FUTURE — not implemented in Phase 1.**  
> No PDF libraries, object storage, or document tables yet.

## Principle

```text
Authoritative business record (PostgreSQL)
        ↓
Validated Document DTO (already-calculated totals)
        ↓
DocumentRenderer / PdfService
        ↓
PDF bytes
        ↓
On-demand download  OR  StorageService → download / email / public link
```

- The **database record** is authoritative.
- The **PDF** is a rendered business document.
- Do **not** recalculate money inside templates.
- Do **not** treat “PDF generated” as financial truth without the underlying invoice/payment state.

## Document types (future)

Quotation, Invoice, Payment receipt, Credit note (if added), Statement, Report, Purchase/Expense (expense module later — not MVP).

## PDF service

Single abstraction: `PdfService` / `DocumentRenderer`.

Choose **one** implementation when the phase arrives (e.g. OpenPDF or PDFBox — license, quality, security, maintenance). Do not add multiple PDF stacks. No dedicated Document microservice in MVP.

## Content

**Quotation:** logo, business/customer details, number, issue/expiry, lines (qty/price/discount/tax), subtotal/total/currency, notes/terms, status as appropriate.

**Invoice:** same plus due date, amount paid, balance due, payment status (PAID / PARTIALLY PAID / UNPAID/OVERDUE).

**Receipt:** receipt number, invoice number, customer, amount, method, reference, date, business info.

## Branding

- FREE: subtle “Generated with QuoteFlow” where appropriate  
- PRO/BUSINESS: custom logo/footer; remove QuoteFlow branding per plan  
- No unsafe arbitrary HTML/JS templates

## Storage evolution

1. Generate on demand for small docs  
2. Persist to object storage when beneficial: `tenant/{tenantId}/invoices/{invoiceId}/invoice.pdf`  
3. Never trust client-supplied storage keys; authorize private downloads  

Finalized immutable PDFs may be reused (cache); drafts regenerate. Prefer background jobs only for large/bulk generation — not for typical 1–2 page invoices until measured need.

## Historical accuracy

Finalized invoices should not silently pick up later logo/address changes. Prefer snapshot fields on the invoice and/or stored final PDF/version metadata. Optional `DocumentVersion` (type, id, version, storageKey, hash, createdAt/By) when compliance justifies — not MVP by default.

## Security

Authenticated downloads: user + tenant + resource authorization. Public links: high-entropy tokens only.

Never embed in PDFs: API keys, tokens, filesystem paths, unnecessary internal IDs, secrets.

Sanitize user text; harden template/rendering against injection.

## Email

Finalize → generate/retrieve PDF → `EmailService` → provider → audit result. Respect attachment size limits; secure links may replace large attachments.

## Receipts & statements

Confirmed payment → recalculate balance → assign receipt number (tenant-unique if formal) → PDF/download/email.

Customer statements (date range: invoices, payments, credits, outstanding) as screen/PDF/CSV later.

## Testing (when implemented)

Quotation/invoice/receipt PDFs; correct totals and tenant data; special characters; long names/descriptions; multi-page; empty optionals; large item counts within limits; generation failure handling — not visual-only.

## Cost

PDF generation inside existing backend until load justifies workers. Object storage only when persistence is required. No Document microservice in MVP.

## Phase alignment

Quotation PDF phase → invoice PDF → receipt after payments → statements/credit notes later. See also [PAYMENTS_ARCHITECTURE.md](PAYMENTS_ARCHITECTURE.md).

## Release gate (documents)

Backend-authoritative calculations; PDF totals verified; tenant data verified; private PDF auth; unpredictable public tokens; no sensitive metadata; sanitized templates; finalized historical behavior defined; numbering concurrency tested.
