# Quote Assistant

First user-facing QuoteFlow AI feature (AI Phase 2).

## Purpose

Natural-language quotation request → structured editable draft → user confirmation → existing `QuotationService`.

AI never persists a quotation from the draft endpoint.

## Architecture

```text
Angular Quote Assistant dialog
        ↓
POST /api/v1/ai/quote-assistant/draft
        ↓
QuoteAssistantService
        ↓
AiProvider (Spring AI → Ollama → qwen3:8b)
        ↓
Structured validation
        ↓
CustomerService (tenant-scoped match)
        ↓
FinancialDocumentCalculator (authoritative preview)
        ↓
Editable proposal (no DB write)
        ↓
User confirms → existing CreateQuotationRequest → QuotationService
```

## Spring AI decision

**Integrated: YES** — Spring AI **2.0.0** (BOM) with `spring-ai-starter-model-ollama`.

Compatible with Spring Boot **4.1.1** / Java 21.

QuoteFlow keeps the `AiProvider` boundary. Default adapter: `AI_ADAPTER=spring-ai`. Phase 1 RestClient path remains as `AI_ADAPTER=legacy-rest`.

## Configuration

```text
AI_ENABLED=true
AI_PROVIDER=OLLAMA
AI_ADAPTER=spring-ai
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:8b
```

Recommended local model: **qwen3:8b** (config-driven, not a Java invariant). Manual fallback: `qwen3:4b`.

## Financial safety

AI is **not** authoritative for totals. Preview totals come from `FinancialDocumentCalculator` only.

## Tenant / security

- Authenticated endpoint only
- Tenant from JWT principal
- Customer lookup via `CustomerService` only
- No business repositories in AI provider packages
- Prompts/responses not logged at INFO
- Per-user/tenant in-memory rate limits (per instance)
- Prompt injection cannot reach the database (no tools; minimized prompt)

## Feature flag UX

`GET /api/v1/ai/capabilities` — UI shows “Draft with AI” only when AI is enabled. Manual quotation always works.

## Failure behavior

Ollama down / timeout / invalid JSON → clear error; continue manually.

## Evaluation

See `docs/AI_LOCAL_DEVELOPMENT.md`. Live smoke:

```bash
# Windows PowerShell
$env:QUOTEFLOW_AI_LIVE="true"
./mvnw -Dtest=Qwen3LiveSmokeIT test
```

## Future

Business Copilot, tool calling, RAG, commercial AI quotas — later AI phases. Tool calls must go through allowlisted tools → business services → authz/tenant rules.
