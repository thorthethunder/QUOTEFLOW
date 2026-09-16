# QuoteFlow AI Architecture (FUTURE)

> **Status: NOT IMPLEMENTED.**  
> Phase 17 documents the roadmap only. Do not add AI SDKs, migrations, or agent runtimes until **AI Phase 1**.

## Principle

AI is an **optional platform capability**. Core SaaS must work when AI is disabled or offline:

- customers, quotations, invoices, payments, PDF, dashboard, billing

AI must **never** call repositories or the database directly.

```text
AI Agent
  → approved Tool
  → existing Spring business service
  → authorization + tenant validation + business rules
  → transaction
  → database
```

## Provider abstraction (required design)

```text
AiProvider
├── OllamaAiProvider          (local / self-hosted)
├── ManagedCloudAiProvider    (configurable cloud vendor)
└── future providers
```

Application code depends on `AiProvider`, not a vendor SDK.

### Local development

```text
Angular → Spring Boot → AiProvider → Ollama → local model
```

Conceptual configuration (do not commit secrets or hardcode a model name):

```text
AI_ENABLED=true
AI_PROVIDER=OLLAMA
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=<operator-configured-model>
```

### Production

```text
Angular → Spring Boot → AiProvider → configured managed or self-hosted provider
```

```text
AI_ENABLED=false|true
AI_PROVIDER=OLLAMA|MANAGED_CLOUD|...
AI_API_KEY=                 # backend secret manager only
AI_MODEL=
AI_EMBEDDING_MODEL=
```

Keys never ship to Angular / `config.json`.

## Target runtime flow

```text
Angular
  ↓
AI API (FUTURE)
  ↓
AI Orchestrator
  ↓
Agent Runtime
  ↓
Tool Registry (allowlisted tools only)
  ↓
Business Service
  ↓
Authorization / Tenant Isolation
  ↓
Database / External Integration
```

## Phased roadmap (post–Phase 17)

| Phase | Scope |
|-------|--------|
| **AI Phase 1** | Provider foundation + **Ollama local support** + feature flag; health isolation |
| **AI Phase 2** | Quote Assistant (draft text / structured suggestion → DRAFT only) |
| **AI Phase 3** | Read-only Business Copilot |
| **AI Phase 4** | Controlled agent tools (allowlisted, authz enforced) |
| **AI Phase 5** | Payment Reminder Assistant (propose → human approve → send) |
| **AI Phase 6** | Reporting insights (read-only aggregates) |
| **AI Phase 7** | Tenant-isolated RAG (prefer PostgreSQL + pgvector first) |
| **AI Phase 8** | Multi-step agent workflows + human approval |
| **AI Phase 9** | Usage / cost / entitlements metering |
| **AI Phase 10** | AI security / red-team (injection, tool misuse, cross-tenant) |

**Ollama local testing support:** PLANNED FOR AI PHASE 1  
**Production AI provider abstraction:** PLANNED  
**AI implementation:** NOT STARTED

## Read vs side-effect actions

- **Read-only** (may auto-run after auth): search, list, summarize, draft text.
- **Side effects** (require human approval): send email/quotation/invoice, delete, subscription changes, bulk contact, financial mutations beyond creating a **DRAFT**.

## Security requirements (FUTURE)

1. AI output is untrusted input.
2. Validate all tool inputs; verify IDs against the tenant.
3. Never bypass authorization or subscription limits.
4. Never put secrets in prompts or logs.
5. Minimize PII sent to external models.
6. Defend against prompt injection and cross-tenant leakage.
7. High-impact actions need approval; tool runs must be auditable.
8. AI outage must not break core SaaS HTTP APIs.

## Planned entities (not migrated yet)

`AiConversation`, `AiMessage`, `AiAgentRun`, `AiToolExecution`, `AiUsageRecord`, `AiApprovalRequest`, `AiKnowledgeDocument`, `AiEmbeddingReference`

Do not create AI Flyway migrations until the corresponding AI phase.

## Incorrect vs correct integration

**Incorrect:** `QuotationService` calling a model vendor SDK directly.

**Correct:** Assistant/Agent → `CreateQuotationTool` → `QuotationService` (auth + tenant + validation) → DRAFT only → user confirms before send.
