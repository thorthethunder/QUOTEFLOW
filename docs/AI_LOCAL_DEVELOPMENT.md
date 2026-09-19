# QuoteFlow AI — local development

Optional subsystem. **Default:** `AI_ENABLED=false`. Core SaaS works without Ollama.

## Why qwen3:8b

Selected as the initial **local development** target for instruction following, structured extraction, and tool-oriented workflows. It is **not** automatically the production model.

**Lower-resource fallback (manual only):** `qwen3:4b` — never silently switch.

## Install Ollama + model

```text
STEP 1  Install Ollama (https://ollama.com)
STEP 2  ollama pull qwen3:8b
STEP 3  ollama list
STEP 4  ollama run qwen3:8b
```

QuoteFlow **never** auto-downloads multi-GB models on startup or in CI.

## Configure Spring Boot

```text
AI_ENABLED=true
AI_PROVIDER=OLLAMA
AI_ADAPTER=spring-ai
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:8b
```

Optional: `OLLAMA_CONNECT_TIMEOUT`, `OLLAMA_READ_TIMEOUT`, Copilot rate/tool limits (`AI_COPILOT_*`),
action approval (`AI_ACTIONS_ENABLED`, `AI_ACTION_APPROVAL_TTL`, `AI_ACTION_RATE_*`).

With `AI_ENABLED=false`, Ollama need not be running. Production should keep `AI_ACTIONS_ENABLED=false` until explicitly validated.

## Features

| Feature | Path | Notes |
|---------|------|--------|
| Quote Assistant | Quotation editor → Draft with AI | Structured draft; does not persist |
| Business Copilot | `/app/copilot` | Read-only tools + Phase 4–5 action proposals; see [BUSINESS_COPILOT.md](BUSINESS_COPILOT.md) |
| Action approvals | Copilot / invoice UI → Review panel | Human confirm; [AI_ACTION_APPROVALS.md](AI_ACTION_APPROVALS.md), [AI_PAYMENT_REMINDERS.md](AI_PAYMENT_REMINDERS.md) |

## Optional Docker Compose profile

```bash
docker compose --profile ai up -d
```

| Spring runs on | `OLLAMA_BASE_URL` |
|----------------|-------------------|
| Host machine | `http://localhost:11434` |
| Compose network | `http://ollama:11434` |

## Live smokes (not CI)

```bash
set QUOTEFLOW_AI_LIVE=true
cd backend
.\mvnw.cmd -Dtest=Qwen3LiveSmokeIT test
.\mvnw.cmd -Dtest=Qwen3ToolCallingLiveIT test
```

## Boundaries

- No repository access from AI tool packages
- AI is never authoritative for money
- Tool arguments are untrusted; tenant from auth context
- Action tools prepare proposals only; confirm is a separate authenticated API (no LLM)
- Prompts/responses not logged at INFO
- `reminder_prepare` does not send email
- `payment_reminder_send` queues email only after human confirm → NotificationService outbox ([AI_PAYMENT_REMINDERS.md](AI_PAYMENT_REMINDERS.md))
- External email delivery remains **at-least-once** at the provider layer

## Health

AI outage must not mark the application DOWN. See `AiHealthIndicator`.

## Reporting Insights

Business Insights is available at `/app/insights`.

```text
AI_REPORTING_INSIGHTS_MAX_MESSAGE=1000
AI_REPORTING_INSIGHTS_MAX_OUTSTANDING_ROWS=5
AI_REPORTING_INSIGHTS_RATE_USER=6
AI_REPORTING_INSIGHTS_RATE_TENANT=20
```

With `AI_ENABLED=false`, the endpoint still returns deterministic facts, bounded evidence, references, and warnings.
See [AI_REPORTING_INSIGHTS.md](AI_REPORTING_INSIGHTS.md).

## Business Knowledge

Business Knowledge is available at `/app/knowledge` when enabled.

```text
AI_KNOWLEDGE_ENABLED=true
AI_EMBEDDING_PROVIDER=OLLAMA
OLLAMA_EMBEDDING_MODEL=mxbai-embed-large
AI_KNOWLEDGE_EMBEDDING_DIMENSION=1024
```

Start PostgreSQL first; embeddings are stored in QuoteFlow tables and retrieved with tenant-filtered SQL.
QuoteFlow does not automatically pull embedding models. Use `ollama list` and `ollama pull mxbai-embed-large`
manually if needed. See [AI_BUSINESS_KNOWLEDGE.md](AI_BUSINESS_KNOWLEDGE.md).
