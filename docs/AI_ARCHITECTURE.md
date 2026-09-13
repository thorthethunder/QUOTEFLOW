# QuoteFlow AI Architecture (FUTURE)

> **Status: FUTURE — not implemented in Phase 1.**  
> This document describes intended design only. No AI APIs, SDKs, migrations, or agent runtimes are active yet.

## Principle

AI is a **platform capability**, not a shortcut into business services.

Core services (`Customer`, `Quotation`, `Invoice`, `Payment`) must work when AI providers are down. AI reaches business logic only through authorized **tools**.

## Target flow

```text
Angular
   ↓
AI API  (FUTURE)
   ↓
AI Orchestrator
   ↓
Agent Runtime
   ↓
Tool Registry
   ↓
Business Service
   ↓
Authorization / Tenant Isolation
   ↓
Database / External Integration
```

An **AI Provider** abstraction sits beside the orchestrator (text generation, structured output, embeddings, tool-capable calls). Application code must not depend on a single vendor SDK.

Conceptual providers (not implemented): OpenAI, Gemini, Anthropic, local models.

## Capabilities planned later

| Capability | Intent |
|------------|--------|
| Provider abstraction | `AiProvider` with secure server-side API keys |
| Assistant | Conversational UI with persisted conversations/messages |
| Structured output | Schema-validated JSON before any create/update |
| Tool calling | Registered tools with auth, tenant checks, validation |
| RAG | Tenant-aware retrieval; consider PostgreSQL + pgvector first |
| Agent runtime | Multi-step workflows (e.g. collections) with orchestration |
| Human approval | Side-effect actions require explicit user confirmation |
| Usage metering | Tokens/requests/cost per tenant and subscription limits |
| Auditing | Tool executions, approvals, and agent runs recorded |

## Read vs side-effect actions

- **Read-only** (may auto-run after auth): search customers, list invoices, summarize revenue, draft text.
- **Side effects** (require human approval): send quotation/invoice/email, delete records, change subscriptions, bulk contact, financial mutations beyond creating a **DRAFT**.

Example: “Send reminders to overdue customers” → agent proposes a summary → user approves → then execute.

## Security requirements (FUTURE)

1. AI output is untrusted input.
2. Validate all tool inputs; verify IDs against the tenant.
3. Never bypass authorization or subscription limits.
4. Never put secrets in prompts or logs.
5. Minimize PII/business data sent to external models.
6. Defend against prompt injection, tool misuse, and cross-tenant leakage.
7. High-impact actions need approval; activity must be auditable.

## Configuration (FUTURE env vars)

```text
AI_PROVIDER=
AI_MODEL=
AI_API_KEY=
AI_EMBEDDING_MODEL=
```

Keys stay on the backend. Never expose to Angular.

## Planned entities (not migrated yet)

`AiConversation`, `AiMessage`, `AiAgentRun`, `AiToolExecution`, `AiUsageRecord`, `AiApprovalRequest`, `AiKnowledgeDocument`, `AiEmbeddingReference`

Do not create AI Flyway migrations until the corresponding AI phase.

## Incorrect vs correct integration

**Incorrect:** `QuotationService` calling OpenAI/Gemini directly.

**Correct:** Assistant/Agent → `CreateQuotationTool` → `QuotationService` (auth + tenant + validation) → DRAFT only → user confirms before send.

## Cost and failure isolation (FUTURE)

- Meter usage and enforce subscription quotas so AI cannot create uncontrolled bills.
- AI provider outage must not break quotation/invoice workflows.
- Prefer streaming/background jobs for long AI operations; keep normal SaaS APIs responsive.
- Start with **one** provider behind `AiProvider`; add others only when product need justifies cost/complexity.

