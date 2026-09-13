# QuoteFlow Documents (PDF)

## Status

| Area | State |
|------|--------|
| Quotation PDF (authenticated, on-demand) | **IMPLEMENTED** (Phase 7) |
| Invoice / receipt PDF | Invoice PDF **DEFERRED**; receipt PDF **IMPLEMENTED** (Phase 9) |
| Email attachment / public link | **FUTURE** |
| Object storage for generated files | **FUTURE** |

## Library

**OpenPDF 3.0.5** (`com.github.librepdf:openpdf`) — LGPL-2.1 / MPL-2.0.

Chosen for table layout, Unicode via embedded fonts, no HTML/JS rendering path, free/OSS, runs in-process (zero infra cost).

## Architecture

```text
GET /api/v1/quotations/{id}/pdf
  → QuotationPdfService (tenant lookup)
  → QuotationPdfDocumentFactory (snapshots + persisted totals + invariant check)
  → QuotationPdfRenderer (OpenPDF + DejaVu fonts)
  → application/pdf bytes
```

PDFs are **not stored**. SENT snapshots make regeneration deterministic for core content.

## Snapshots

Customer + Business snapshots live on `quotations`. DRAFT create/update refreshes both; send freezes business snapshot once more; SENT never refreshes. PDF never reads live Customer/Business rows for identity.

## Fonts

Bundled `DejaVuSans` / `DejaVuSans-Bold` under `classpath:fonts/` (Bitstream Vera / Arev license). Supports ₹, $, €, £ and common Latin punctuation. Full Unicode/Indic scripts not claimed.

## Status markings

DRAFT and CANCELLED are highlighted on the PDF. SENT is shown without alarm styling.

## Security

- Authenticated only; tenant `id + businessId`
- Cross-tenant → 404
- `Cache-Control: private, no-store`
- Filename from quotation number only
- Plain-text rendering (no HTML)
- No remote font/image fetch
- Metadata title = quotation number only

## Branding entitlement

FREE plans show “Generated with QuoteFlow” (and receipt footer · QuoteFlow). PRO/BUSINESS remove branding.

Historical policy: branding entitlement is **snapshotted** at quotation SEND and payment record (`show_quoteflow_branding`). DRAFT PDFs may follow the live plan. Regenerating a SENT PDF after upgrade/downgrade does not rewrite the frozen branding flag.

See [SUBSCRIPTIONS.md](SUBSCRIPTIONS.md) / [ADR-016](adr/ADR-016-saas-entitlement-and-usage-limit-strategy.md).

## Future

StorageService → R2/S3; email attach; public token page; invoice PDF reuse of money/layout helpers.

See [ADR-012](adr/ADR-012-pdf-rendering-strategy.md).
