# ADR-011: Historical Document Snapshot Strategy

## Status

Accepted (Phase 6)

## Context

Editing a Customer must not rewrite already issued quotation identity.

## Decision

Persist customer and business snapshot columns on `quotations` at create/DRAFT update (business also refreshed at send). Keep `customer_id` / `business_id` FKs. SENT snapshots are immutable via DRAFT-only edits + no PDF-time refresh. Letterhead comes from business snapshot columns (Phase 7).

## Consequences

Historical documents and PDFs remain stable; future invoices should copy/snapshot similarly.
