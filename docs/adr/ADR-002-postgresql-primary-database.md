# ADR-002: PostgreSQL as Primary Database

- **Status:** Accepted
- **Date:** 2026-09-13
- **Phase:** 1

## Context

QuoteFlow needs transactional integrity for financial documents (quotations, invoices, payments) and a low fixed-cost MVP data store.

## Decision

**PostgreSQL** is the authoritative system of record. Schema evolution is owned by **Flyway**. Hibernate production mode is **`ddl-auto: validate`** (no automatic production schema mutation). Connection pooling uses Spring Boot **HikariCP** defaults until measured need justifies tuning.

## Alternatives

1. Multiple specialized databases from day one — rejected: cost and complexity.
2. Hibernate auto-DDL in production — rejected: unsafe schema control.
3. Store critical financial state in Redis/cache — rejected: durability and consistency risk.

## Consequences

- Clear backup/restore story on managed PostgreSQL.
- Indexes and pagination must be designed with tenant query patterns in later phases.
- Optional Redis/Kafka/vector stores may be added later; they do not replace PostgreSQL as source of truth.
