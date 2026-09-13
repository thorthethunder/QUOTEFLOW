# ADR-010: Tenant Document Numbering Strategy

## Status

Accepted (Phase 6)

## Context

`MAX(number)+1` races under concurrency. Tenants need independent sequences.

## Decision

`document_sequences(business_id, document_type)` with pessimistic row lock, atomic increment, display format `Q-%06d`, uniqueness `(business_id, quotation_number)`.

## Consequences

Safe concurrent allocation; independent tenant counters; gaps possible on rollback (not claimed gapless).
