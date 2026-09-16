# QuoteFlow AI Architecture

> **Status: AI Phase 1 IMPLEMENTED** — provider foundation + Ollama.  
> User-facing assistants / agents / RAG: **not started** (AI Phase 2+).

## Principle

AI is an **optional platform capability**. Core SaaS must work when:

- `AI_ENABLED=false`, or
- Ollama (or any provider) is offline

Must not break: auth, customers, quotations, invoices, payments, PDF, dashboard, billing, notifications.

AI must **never** call repositories or the database directly.

```text
AI Agent (future)
  → approved Tool
  → existing Spring business service
  → authorization + tenant validation + business rules
  → transaction
  → database
```

## Provider abstraction (Phase 1)

```text
AiProvider
├── DisabledAiProvider     (AI_ENABLED=false)
├── OllamaAiProvider       (AI_ENABLED=true, AI_PROVIDER=OLLAMA)
└── future: managed cloud / self-hosted providers
```

Package: `com.quoteflow.ai` — explicit QuoteFlow-owned code (no LangChain / Spring AI in Phase 1).

Local development guide: [AI_LOCAL_DEVELOPMENT.md](AI_LOCAL_DEVELOPMENT.md).

### Local development

```text
Angular → Spring Boot → AiProvider → Ollama → qwen3:8b (configured)
```

```text
AI_ENABLED=true
AI_PROVIDER=OLLAMA
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:8b
```

Lower-resource **manual** fallback: `OLLAMA_MODEL=qwen3:4b` (never silent).

### Production

```text
AI_ENABLED=false          # default — required until operator enables AI
AI_PROVIDER=OLLAMA        # only implemented provider today
OLLAMA_BASE_URL=          # required only when AI enabled
OLLAMA_MODEL=
```

Keys for future managed providers: backend secret manager only — never Angular / `config.json`.

## Financial safety

**AI totals are not authoritative.**  
Authoritative money: `FinancialDocumentCalculator` / `PaymentSummaryCalculator`.

Future Quote Assistant flow:

```text
NL → model → proposed structured draft → validation → FinancialDocumentCalculator → user confirm → QuotationService
```

## Structured output

`generateStructured` uses Ollama `format` JSON Schema when available, then `StructuredOutputValidator` (size, JSON, Jakarta Validation). Demo type: `QuotationDraftProposal` (not persisted).

## Telemetry

`AiUsageRecorder` logs provider/model/feature/latency/tokens/success — not prompts or PII payloads.

Local Ollama: provider API monetary cost is typically zero; **compute/infrastructure cost is not zero**.

## Health

`AiHealthIndicator` always contributes **UP** with `aiAvailable` detail so provider outage cannot mark the app unhealthy. Readiness group remains DB-centric.

## Security (Phase 1 + future)

1. Model output is untrusted.
2. No SSRF: base URL never from client payloads.
3. Bounded response size and timeouts; no blind generation retries.
4. No prompt logging by default (`AI_LOG_PROMPTS` forbidden in prod).
5. Future tools: allowlist + schema + authz + tenant checks (never free-form backend ops from model text).
6. Prompt injection defense expands in AI Phase 10.

## Phased roadmap

| Phase | Scope | Status |
|-------|--------|--------|
| **AI Phase 1** | Provider foundation + Ollama + qwen3:8b local | **THIS PHASE** |
| **AI Phase 2** | Quote Assistant | NEXT |
| **AI Phase 3** | Read-only Business Copilot | Planned |
| **AI Phase 4** | Controlled agent tools | Planned |
| **AI Phase 5** | Payment Reminder Assistant | Planned |
| **AI Phase 6** | Reporting insights | Planned |
| **AI Phase 7** | Tenant-isolated RAG | Planned |
| **AI Phase 8** | Agent workflows + human approval | Planned |
| **AI Phase 9** | Usage / cost / entitlements | Planned |
| **AI Phase 10** | AI security / red-team | Planned |

## Evaluation foundation (expand in Phase 2)

Categories to score providers/models later: simple EN quotes, messy WhatsApp-style text, missing qty/price, multi-service, discount/tax asks, long/ambiguous units, Tamil / mixed language, typos, malicious instructions, prompt injection, huge input, invalid money formats, invalid JSON.

Production model decision must use QuoteFlow benchmarks (accuracy, latency, RAM/VRAM, cost, privacy, ops) — not generic leaderboards alone. Strategies: managed API, dedicated Ollama/self-hosted, or hybrid.

## Incorrect vs correct

**Incorrect:** `QuotationService` calling a vendor SDK; AI writing totals; AI choosing tenant.

**Correct:** Assistant → Tool → `QuotationService` (auth + tenant + validation) → DRAFT → user confirms.
