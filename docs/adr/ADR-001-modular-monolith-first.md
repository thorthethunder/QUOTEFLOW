# ADR-001: Modular Monolith First

- **Status:** Accepted
- **Date:** 2026-09-13
- **Phase:** 1

## Context

QuoteFlow must support future multi-product SaaS scale (optional Redis, workers, Kafka, microservices) without paying for distributed infrastructure during early MVP.

## Decision

Ship **one** Spring Boot deployable application with **strong internal module boundaries**. Introduce feature packages only when features are implemented. Prefer application-service / interface / domain-event boundaries over cross-module repository access.

## Alternatives

1. Start with microservices — rejected: operational and cost overhead unjustified at current scale.
2. Unstructured single codebase — rejected: blocks clean extraction and platform reuse later.

## Consequences

- Simple deploy path (Railway-compatible single service).
- Modules remain extractable when scaling/security/team ownership justify it.
- Discipline required: do not shortcut module boundaries as features grow.
