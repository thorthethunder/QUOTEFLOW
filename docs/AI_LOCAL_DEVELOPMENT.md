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

Optional: `OLLAMA_CONNECT_TIMEOUT`, `OLLAMA_READ_TIMEOUT`, Copilot rate/tool limits (`AI_COPILOT_*`).

With `AI_ENABLED=false`, Ollama need not be running.

## Features

| Feature | Path | Notes |
|---------|------|--------|
| Quote Assistant | Quotation editor → Draft with AI | Structured draft; does not persist |
| Business Copilot | `/app/copilot` | Read-only tools; see [BUSINESS_COPILOT.md](BUSINESS_COPILOT.md) |

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
- Prompts/responses not logged at INFO

## Health

AI outage must not mark the application DOWN. See `AiHealthIndicator`.
