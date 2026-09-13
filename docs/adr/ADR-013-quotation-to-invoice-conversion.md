# ADR-013: Quotation-to-Invoice Conversion Strategy

## Status

Accepted (Phase 8)

## Context

Invoices must be historical financial documents. Quotation values must not be live-inherited after conversion. Concurrent UI retries must not create multiple invoices for one quotation.

## Decision

1. **Independent document** — Invoice owns its own UUID, `INV-######` number, item rows, money inputs/outputs, and customer/business snapshots.
2. **Provenance only** — nullable `source_quotation_id` with FK `ON DELETE RESTRICT`. Not live inheritance.
3. **One quotation → one invoice** — PostgreSQL `UNIQUE (source_quotation_id)` (multiple NULLs allowed for standalone invoices).
4. **Eligibility** — only `SENT` quotations may convert. DRAFT and CANCELLED are rejected.
5. **Snapshots** — copy quotation historical customer/business snapshots (not live Customer/Business at conversion time).
6. **Money** — copy calculation inputs; recalculate with `FinancialDocumentCalculator`; abort if totals disagree with quotation authoritative amounts.
7. **Transaction** — conversion runs in one DB transaction; partial invoice/item state is not committed.
8. **Conflicts** — sequential or concurrent duplicates return HTTP 409 with stable code `QUOTATION_ALREADY_INVOICED`.
9. **Lifecycle** — converted invoice starts `DRAFT`; quotation status is unchanged (no `INVOICED` status). Association is exposed via `convertedInvoiceId`.
10. **Payments** — deferred to Phase 9; document status remains separate from payment state.

## Consequences

Strong historical integrity and idempotent conversion. Partial/multiple invoices per quotation are out of scope until product policy changes (would require dropping/replacing the unique constraint deliberately).
