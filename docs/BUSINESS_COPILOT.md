# Business Copilot (AI Phase 3)

> **Status: IMPLEMENTED** — read-only Copilot with allowlisted Spring AI tools.  
> Production AI deployment: **NOT STARTED** (`AI_ENABLED=false` by default).

## What it is

Angular **Business Copilot** (`/app/copilot`) answers questions about the authenticated tenant’s existing QuoteFlow data by calling **explicitly approved READ_ONLY tools**.

Examples:

- Who hasn't paid me yet?
- How much have I collected this month?
- Show partially paid invoices.
- Find quotations for Raj Electrical.

The LLM **summarizes**; tools + business/reporting services are **authoritative**.

## Architecture

```text
Angular Business Copilot
        ↓
BusinessCopilotController  POST /api/v1/ai/copilot/ask
        ↓
BusinessCopilotService
        ↓
Spring AI ChatClient + allowlisted ToolCallbacks
        ↓
QuoteFlow AiToolRegistry (fail closed)
        ↓
Customer / Quotation / Invoice / Payment / Reporting services
        ↓
AuthN + AuthZ + tenant enforcement
        ↓
PostgreSQL
```

The model is **never** authorization.

## Tool allowlist (Phase 3)

| Tool | Purpose |
|------|---------|
| `customer_lookup` | Bounded customer search |
| `quotation_search` | Bounded quotation search / period sample |
| `invoice_search` | Invoices by document status and/or payment state |
| `payment_status` | Outstanding / unpaid / partial via PaymentSummaryCalculator |
| `business_summary` | Dashboard metrics via ReportingService |

### Categories

**READ_ONLY** (executable now): tools above.

**ACTION_REQUIRES_APPROVAL** (documented only — Phase 4):

- `quotation.create`, `invoice.create`, `reminder.send`, …

**FORBIDDEN** (never register):

- `repository.direct`, `sql.execute`, `tenant.switch`, `subscription.forceChange`

Registration is explicit via `QuoteFlowAiTool` beans + `AiToolRegistry`. No classpath scanning of `@Service` methods.

## Security model

Prompt instructions are **not** the primary control. Controls are:

1. Explicit tool allowlist  
2. Jakarta-validated tool arguments (enums, bounds, no SQL/JPQL)  
3. Trusted tenant from `AuthenticatedUser` / `CopilotToolContext` (passed via Spring AI `toolContext`)  
4. Existing business-service authorization  
5. Bounded rows, date ranges, tool-call count, response size  

If the model is prompt-injected, backend boundaries still hold.

### Tenant context

HTTP request authenticates → principal is placed in an immutable `CopilotToolContext` → Spring AI tool callbacks read that context (not ThreadLocal alone). Model-supplied `businessId` is ignored.

### Multi-currency

Tools return `MoneyByCurrency[]` (or equivalent). **Never** sum INR+USD+EUR. QuoteFlow has no FX conversion.

### Dates / timezone

Periods (`THIS_MONTH`, `LAST_MONTH`, `THIS_WEEK`, `TODAY`, `CUSTOM`) resolve with the **business timezone** (`CopilotPeriodResolver` / reporting semantics).

### Mutations

Copilot does **not** create/update/send/void/email/change settings. Mutation-sounding asks get a clear “not available through Copilot yet” warning path; tools cannot perform writes.

## API

### `GET /api/v1/ai/capabilities`

```json
{
  "enabled": false,
  "quoteAssistant": false,
  "businessCopilot": false,
  "provider": "DISABLED",
  "model": ""
}
```

`businessCopilot` is true only when AI is enabled **and** `AI_ADAPTER=spring-ai`.

### `POST /api/v1/ai/copilot/ask`

Request: `{ "message": "…" }` (max 2000 chars; no client system prompt / tool list / businessId).

Response:

```json
{
  "answer": "…",
  "references": [
    { "type": "INVOICE", "id": "…", "displayNumber": "INV-0015", "label": "INV-0015" }
  ],
  "warnings": []
}
```

Frontend builds routes from trusted `type` + `id` — never from model-invented URLs. Answer text is rendered as plain text (no `innerHTML`).

## Limits (operational + hard caps)

| Setting | Default | Hard max |
|---------|---------|----------|
| Message length | 2000 | 2000 |
| Tool calls / request | 6 | 8 |
| Result rows / tool | ≤20 | 20 |
| Rate / user / minute | 6 | — |
| Rate / tenant / minute | 20 | — |

Per-instance in-memory rate limits (same pattern as Quote Assistant). Redis not required for Phase 3.

## Telemetry

`AiFeature.BUSINESS_COPILOT` via `AiUsageRecorder`: provider, model, latency, tokens, success/failure, tool call **count**. Does **not** log prompts, tool payloads, or business PII.

## Configuration

```text
AI_ENABLED=false
AI_ADAPTER=spring-ai
AI_COPILOT_MAX_MESSAGE=2000
AI_COPILOT_MAX_TOOL_CALLS=6
AI_COPILOT_RATE_USER=6
AI_COPILOT_RATE_TENANT=20
```

## Local qwen3 tool smoke

```bash
set QUOTEFLOW_AI_LIVE=true
cd backend
.\mvnw.cmd -Dtest=Qwen3ToolCallingLiveIT test
```

Normal Maven CI tests do **not** require Ollama.

## Future Phase 4 — controlled actions (document only)

```text
User → Copilot → model proposes action → Action Tool
  → authorization → tenant validation → business validation
  → APPROVAL REQUIRED → user confirms exact action
  → business service → transaction / audit
```

Do not execute mutations from the model alone.

## Related

- [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md)
- [AI_LOCAL_DEVELOPMENT.md](AI_LOCAL_DEVELOPMENT.md)
- [QUOTE_ASSISTANT.md](QUOTE_ASSISTANT.md)
- [SECURITY.md](SECURITY.md)
