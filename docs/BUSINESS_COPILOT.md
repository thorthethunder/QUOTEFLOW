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

**ACTION_REQUIRES_APPROVAL** (Phase 4–5 — prepare only, human confirm required):

- `quotation_create_draft`, `invoice_create_draft`, `reminder_prepare` (no email)
- `payment_reminder_send` (human-approved email queue)

See [AI_ACTION_APPROVALS.md](AI_ACTION_APPROVALS.md) and [AI_PAYMENT_REMINDERS.md](AI_PAYMENT_REMINDERS.md).

**FORBIDDEN** (never register):

- `repository.direct`, `sql.execute`, `tenant.switch`, `subscription.forceChange`, `payment.record`, `confirm_action`, …

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

### Mutations / actions

Read-only tools never write. Phase 4 **action** tools only **prepare** an `ActionProposal` (`PENDING`). Persistence of quotations/invoices (and any email send) happens only after a separate authenticated human **confirm** API call — never from model output alone.

## API

### `GET /api/v1/ai/capabilities`

```json
{
  "enabled": false,
  "quoteAssistant": false,
  "businessCopilot": false,
  "aiActions": false,
  "provider": "DISABLED",
  "model": ""
}
```

`businessCopilot` is true only when AI is enabled **and** `AI_ADAPTER=spring-ai`.  
`aiActions` is true only when AI actions are enabled (`AI_ACTIONS_ENABLED`).

### `POST /api/v1/ai/copilot/ask`

Request: `{ "message": "…" }` (max 2000 chars; no client system prompt / tool list / businessId).

Response:

```json
{
  "answer": "…",
  "references": [
    { "type": "INVOICE", "id": "…", "displayNumber": "INV-0015", "label": "INV-0015" }
  ],
  "warnings": [],
  "actionProposal": null
}
```

When an action tool prepares a proposal, `actionProposal` is a summary (`proposalId`, `actionType`, `status`, `expiresAt`, `summary`, `confirmButtonLabel`). The Angular approval panel loads full details via `GET /api/v1/ai/actions/{id}` and confirms via `POST .../confirm`.
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

## Phase 4 — controlled actions

Implemented. See [AI_ACTION_APPROVALS.md](AI_ACTION_APPROVALS.md).

```text
User → Copilot → action tool PREPARE ONLY
  → ActionProposal PENDING → Angular review
  → POST /api/v1/ai/actions/{id}/confirm (human)
  → existing QuotationService / InvoiceService
```

Do not execute mutations from the model alone. Reminder **send** is Phase 5.

## Related

- [AI_ACTION_APPROVALS.md](AI_ACTION_APPROVALS.md)
- [AI_BUSINESS_KNOWLEDGE.md](AI_BUSINESS_KNOWLEDGE.md)
- [AI_REPORTING_INSIGHTS.md](AI_REPORTING_INSIGHTS.md)
- [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md)
- [AI_LOCAL_DEVELOPMENT.md](AI_LOCAL_DEVELOPMENT.md)
- [QUOTE_ASSISTANT.md](QUOTE_ASSISTANT.md)
- [SECURITY.md](SECURITY.md)

Business Knowledge (`/app/knowledge`) is a separate tenant-isolated RAG surface. It retrieves tenant-owned policy text with no mutation/action tools; Copilot action approvals remain a separate workflow.

## Controlled workflows

AI Phase 8 adds `/app/workflows` for durable workflow coordination. The first workflow, `PAYMENT_FOLLOW_UP`, prepares
existing payment reminder action proposals and waits for the user to approve them. It is separate from Copilot chat and
does not grant the model any confirm or send capability.

See [AI_AGENT_WORKFLOWS.md](AI_AGENT_WORKFLOWS.md).
