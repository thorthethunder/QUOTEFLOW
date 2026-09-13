# ADR-012: PDF Rendering Strategy

## Status

Accepted (Phase 7)

## Context

Quotations need professional PDFs without browser print, HTML engines, or paid SaaS.

## Decision

- Generate PDFs **on demand** inside Spring Boot with **OpenPDF 3.x**.
- Render from `QuotationPdfDocument` built from **persisted** snapshots and totals.
- Embed **DejaVu** fonts from the classpath (no OS font dependency).
- Do **not** accept tenant HTML, fetch remote resources, or persist PDF blobs yet.
- Authenticated download only: `GET /api/v1/quotations/{id}/pdf`.

## Consequences

Zero storage cost; deterministic SENT documents; reusable path for email/invoice later. Not PDF/UA accessibility certified.
