# QuoteFlow AI Architecture

> **Status: AI Phase 3 IMPLEMENTED** — provider foundation + Spring AI Ollama + Quote Assistant + read-only Business Copilot.  
> Agents / mutation tools / RAG / production AI deploy: **not started**.

## Principle

AI is an **optional platform capability**. Core SaaS must work when:

- `AI_ENABLED=false`, or
- Ollama (or any provider) is offline

Must not break: auth, customers, quotations, invoices, payments, PDF, dashboard, billing, notifications.

AI must **never** call repositories or the database directly.

```text
Business Copilot / future Agent
  → approved Tool (allowlist)
    → existing Spring business service
    → authorization + tenant validation + business rules
    → (Phase 3: read only) / (Phase 4+: approval then mutate)
    → database
```

## Provider abstraction

```text
AiProvider
├── DisabledAiProvider     (AI_ENABLED=false)
├── SpringAiOllamaProvider (AI_ENABLED=true, adapter=spring-ai)  ← default
├── OllamaAiProvider       (adapter=legacy-rest)
└── future: managed cloud / self-hosted providers
```

Package: `com.quoteflow.ai` — QuoteFlow-owned. Business Copilot tool calling requires `AI_ADAPTER=spring-ai`.

Local development guide: [AI_LOCAL_DEVELOPMENT.md](AI_LOCAL_DEVELOPMENT.md).  
Business Copilot: [BUSINESS_COPILOT.md](BUSINESS_COPILOT.md).  
Quote Assistant: [QUOTE_ASSISTANT.md](QUOTE_ASSISTANT.md).

### Local development

```text
Angular → Spring Boot → AiProvider / ChatClient → Ollama → qwen3:8b (configured)
```

```text
AI_ENABLED=true
AI_PROVIDER=OLLAMA
AI_ADAPTER=spring-ai
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:8b
```

Lower-resource **manual** fallback: `OLLAMA_MODEL=qwen3:4b` (never silent).

### Production

```text
AI_ENABLED=false          # default — required until operator enables AI
AI_PROVIDER=OLLAMA
OLLAMA_BASE_URL=          # required only when AI enabled
OLLAMA_MODEL=
```

Keys for future managed providers: backend secret manager only — never Angular / `config.json`.

## Financial safety

**AI totals are not authoritative.**  
Authoritative money: `FinancialDocumentCalculator` / `PaymentSummaryCalculator` / `ReportingService`.

Multi-currency: preserve `MoneyByCurrency[]` — never sum across currencies without FX.

## Structured output & tools

- Quote Assistant: `generateStructured` + JSON Schema + `StructuredOutputValidator`.
- Business Copilot: Spring AI tool calling → `AiToolRegistry` allowlist → services.

## Telemetry

`AiUsageRecorder` logs provider/model/feature/latency/tokens/success/toolCallCount — not prompts or PII payloads.

Local Ollama: provider API monetary cost is typically zero; **compute/infrastructure cost is not zero**.

## Health

`AiHealthIndicator` always contributes **UP** with `aiAvailable` detail so provider outage cannot mark the app unhealthy. Readiness group remains DB-centric.

## Security

1. Model output is untrusted (including tool arguments).
2. No SSRF: base URL never from client payloads.
3. Bounded response size, tool calls, rows, timeouts.
4. No prompt logging by default (`AI_LOG_PROMPTS` forbidden in prod).
5. Tools: allowlist + schema + authz + tenant checks only.
6. Prompt injection cannot bypass backend tenant/authz boundaries.

## Phased roadmap

| Phase | Scope | Status |
|-------|--------|--------|
| **AI Phase 1** | Provider foundation + Ollama + qwen3:8b local | **CLOSED PASS** |
| **AI Phase 2** | Quote Assistant + Spring AI | **CLOSED PASS** |
| **AI Phase 3** | Read-only Business Copilot | **CLOSED PASS** |
| **AI Phase 4** | Controlled action tools + human approval | **THIS PHASE** |
| **AI Phase 5** | Payment Reminder Assistant + approved sending | NEXT |
| **AI Phase 6** | Reporting insights | Planned |
| **AI Phase 7** | Tenant-isolated RAG | Planned |
| **AI Phase 8** | Agent workflows | Planned |
| **AI Phase 9** | Usage / cost / entitlements | Planned |
| **AI Phase 10** | AI security / red-team | Planned |

## Incorrect vs correct

**Incorrect:** AI writing totals; AI choosing tenant; exposing repositories to the model.

**Correct:** Copilot → allowlisted tool → (read: business service) or (action: prepare proposal → human confirm → business service).

See [AI_ACTION_APPROVALS.md](AI_ACTION_APPROVALS.md) for Phase 4 proposal → confirm → execute.
