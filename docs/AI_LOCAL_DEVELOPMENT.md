# QuoteFlow AI — local development (AI Phase 1)

Optional subsystem. **Default:** `AI_ENABLED=false`. Core SaaS works without Ollama.

## Why qwen3:8b

Selected as the initial **local development** target for a practical balance of:

- instruction following and business-language understanding
- information extraction / structured responses
- reasoning and future tool-oriented workflows
- local hardware requirements

It is **not** automatically the production model. Production choice needs QuoteFlow-specific evaluation later.

**Lower-resource fallback (manual only):** `qwen3:4b`  
Never silently switch models when `qwen3:8b` is missing — report the provider/model error.

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
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:8b
```

Optional timeouts: `OLLAMA_CONNECT_TIMEOUT` (default 5s), `OLLAMA_READ_TIMEOUT` (default 120s).

Then run the backend as usual. With `AI_ENABLED=false`, Ollama need not be running.

## Optional Docker Compose profile

```bash
docker compose --profile ai up -d
```

Plain `docker compose up` does **not** start Ollama.

| Spring runs on | `OLLAMA_BASE_URL` |
|----------------|-------------------|
| Host machine | `http://localhost:11434` |
| Compose network container | `http://ollama:11434` |

Pull the model explicitly (host CLI or `docker exec` into `quoteflow-ollama`).

## Architecture boundaries

```text
AiProvider (QuoteFlow)
  └── OllamaAiProvider → OllamaClient → Ollama HTTP API
```

- No Spring AI / LangChain in Phase 1
- No repository access from AI packages
- AI is never authoritative for money — use `FinancialDocumentCalculator` after validation
- Model output is **untrusted** input (JSON + bean validation)
- Prompts/responses are not logged at INFO (usage metadata only)
- `OLLAMA_BASE_URL` is trusted server config only (SSRF: never from browser)

## Structured smoke (manual)

With Ollama + `qwen3:8b` available, a provider smoke can extract a **non-persisted** `QuotationDraftProposal` from text such as:

> Create a quotation draft for Raj Electrical for 2 ceiling fans at 3000 each, 5 switches at 250 each, wiring work at 1800 and labour at 2500.

Do **not** create quotation rows from this smoke.

## Health

Ollama down ⇒ AI unavailable; application readiness still **UP** (DB still gates readiness).

## Production

- Default remains `AI_ENABLED=false`
- Do not deploy Ollama to Railway in Phase 1
- Future internal Ollama is supported by config only — not production-validated here

See [AI_ARCHITECTURE.md](AI_ARCHITECTURE.md) for roadmap (AI Phase 2 = Quote Assistant).
