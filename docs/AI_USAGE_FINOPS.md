# AI Usage, Entitlements, and FinOps

Phase 9 adds tenant-scoped AI metering for product allowances and provider observability.

## Runtime Contract

- Server-side identity is authoritative. Controllers derive `businessId` and `userId` from the authenticated principal; clients, prompts, tools, and RAG content cannot choose the tenant, plan, allowance, or cost.
- Customer allowances are consumed through `AiEntitlementService` before provider execution.
- Technical provider usage is written separately through `AiUsageLedgerService`.
- The usage ledger stores metadata only. It must not store prompt text, model responses, email bodies, uploaded document text, retrieved chunks, or generated reminder content.
- Allowance enforcement is concurrency-safe. `ai_usage_allowance_buckets` is selected `FOR UPDATE` inside the entitlement transaction before incrementing usage.

## Product Allowances

The Phase 9 allowance taxonomy is:

- `QUOTE_DRAFT`
- `BUSINESS_COPILOT`
- `REPORTING_INSIGHT`
- `PAYMENT_REMINDER`
- `KNOWLEDGE_INGESTION`
- `KNOWLEDGE_QUERY`
- `AGENT_WORKFLOW`

Plan limits are centralized in `AiUsagePolicy`. Current development limits are code-defined:

| Feature | Free | Pro | Business |
| --- | ---: | ---: | ---: |
| Quote drafts | 20 | 100 | 500 |
| Business Copilot | 10 | 100 | 500 |
| Reporting insights | 10 | 100 | 500 |
| Payment reminders | 10 | 100 | 500 |
| Knowledge ingestion | 5 | 50 | 200 |
| Knowledge Q&A | 20 | 250 | 1000 |
| Agent workflows | 3 | 25 | 100 |

`PROVIDER_SMOKE`, `STRUCTURED_SMOKE`, and internal-only AI features are not customer-entitled product features.

## RAG Metering

Knowledge ingestion consumes one `KNOWLEDGE_INGESTION` allowance for each create/upload/replace request. Embedding calls are then logged as technical `EMBEDDING` events with input character and embedding counts.

Knowledge Q&A consumes one `KNOWLEDGE_QUERY` allowance per user question. Query embedding and chat generation are logged as technical events. The customer is not double-charged for embedding plus chat.

## Actions And Workflows

Preparing a payment reminder draft consumes `PAYMENT_REMINDER`. Confirming an already prepared reminder does not consume another AI allowance.

`PAYMENT_FOLLOW_UP` workflows consume one `AGENT_WORKFLOW` allowance per new idempotent workflow. The workflow is deterministic in Phase 8/9 and records no model tokens for workflow execution.

## Cost Catalog

Provider costs are estimated by `AiCostCatalog` with `BigDecimal`.

- Local `OLLAMA` usage is known zero cost and is stored as `0.00000000 USD`.
- Deterministic hash embeddings used in tests/development are known zero cost.
- Unknown provider or model prices are stored as `NULL`, never silently treated as zero.
- Explicit provider cost estimates are stored as historical event snapshots. Later catalog changes must not rewrite existing ledger rows.

The frontend AI usage page intentionally does not display provider cost. Cost data is platform operations metadata, not tenant billing proof.

## Operational Notes

- The current policy is centralized but not yet database-admin configurable.
- Phase 9 meters customer-facing AI allowances and provider API metadata. It does not meter infrastructure spend such as CPU, GPU, RAM, disk, or network egress.
- Failed provider calls may still create technical usage events, but customer allowances are consumed only through the entitlement gate before a customer-facing AI operation is attempted.
- Platform-wide FinOps dashboards, catalog admin screens, ledger exports, retention automation, and adjustment workflows are intentionally deferred.
- Commercial plan changes should update `AiUsagePolicy` and add regression tests for upgrade, downgrade, disabled feature, and limit exhaustion behavior.
- The ledger is append-only at the application layer. Corrections should be additive adjustment events in a future admin workflow, not destructive updates.
- Retention and export policy are future operations work; until then, the ledger contains only metadata suitable for operational usage review.
