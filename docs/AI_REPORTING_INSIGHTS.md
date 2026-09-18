# AI Reporting Insights

> Status: AI Phase 6 implemented. Production AI deployment remains **NOT STARTED**.

## Purpose

Reporting Insights explains tenant business performance using deterministic QuoteFlow reporting data.
AI may summarize and organize observations, but it is not the source of truth for money.

```text
PostgreSQL
  -> ReportingService / ReportingRepository
  -> deterministic metrics + bounded evidence
  -> ReportingInsightService
  -> optional AI narrative
  -> Angular Business Insights
```

## API

`POST /api/v1/ai/insights/analyze`

Request:

```json
{
  "question": "Compare this month with last month.",
  "period": "THIS_MONTH",
  "comparison": "PREVIOUS_PERIOD"
}
```

The request does not accept `businessId`, provider, model, prompt, SQL, or tool configuration.
Tenant identity comes from the authenticated principal.

Response separates:

- `facts`: deterministic amounts, absolute deltas, percentages, and zero-base reasons
- `topOutstandingInvoices`: bounded evidence rows
- `customerOutstanding`: bounded currency-specific concentration rows
- `references`: trusted type/id/display labels for Angular navigation
- `answer`: AI narrative or a deterministic fallback
- `warnings`: AI-disabled/unavailable/timeout notes

## Period Semantics

Supported periods are `THIS_MONTH` and `LAST_MONTH`.
Boundaries are resolved by the backend using the Business timezone and `YearMonth`.
`PREVIOUS_PERIOD` compares `THIS_MONTH` with last month, or `LAST_MONTH` with the month before that.

Dashboard definitions are reused:

- invoiced: SENT invoices by `issue_date`
- collected: RECORDED payments by `payment_date`
- outstanding: current balance due on SENT invoices in the selected issue-date period
- VOIDED payments are excluded
- currencies remain grouped independently

## Deltas

Absolute change:

```text
current - previous
```

Percentage change:

```text
(absoluteChange / previous) * 100
```

Rounding policy: percentage changes are rounded to 2 decimal places using `HALF_UP`.

When previous is zero, `percentageChange` is `null`.
`comparisonReason` is:

- `NO_PREVIOUS_BASE` when current is non-zero and previous is zero
- `NO_ACTIVITY` when both are zero
- `OK` when a percentage is mathematically meaningful

## Multi-Currency

QuoteFlow has no authoritative FX conversion.
INR, USD, EUR, and other currencies are never combined into a fake total.
The UI renders separate currency groups.

## Evidence

Evidence is bounded by `AI_REPORTING_INSIGHTS_MAX_OUTSTANDING_ROWS` (default 5, hard max 10).
Only display-safe fields are returned for insight evidence:

- invoice number, customer display name, currency, totals, paid amount, balance, dates, payment state
- customer display name, currency outstanding, currency total, concentration percent

Recent dashboard invoices/payments are not included in the insight response.

## AI Failure Behavior

If `AI_ENABLED=false`, the provider is unavailable, or generation fails, the API still returns authoritative facts and evidence with a warning and fallback answer.
Normal deterministic tests do not require Ollama.

## Security

- No text-to-SQL, JPQL, repository, EntityManager, or JdbcTemplate is exposed to AI.
- Reporting Insights does not register tool callbacks and does not mutate data.
- Prompt injection through customer/document names remains data; backend tenant filters and authz are the control.
- Angular renders AI text with interpolation and builds links from trusted references only.

## Configuration

```text
AI_REPORTING_INSIGHTS_MAX_MESSAGE=1000
AI_REPORTING_INSIGHTS_MAX_OUTSTANDING_ROWS=5
AI_REPORTING_INSIGHTS_RATE_USER=6
AI_REPORTING_INSIGHTS_RATE_TENANT=20
```

## Limitations

Not implemented in Phase 6:

- forecasting
- cash-flow prediction
- customer risk scoring
- tax/accounting/legal advice
- RAG, embeddings, persistent AI memory
- autonomous agent workflows
- AI commercial quotas/cost controls
- managed AI providers
- production AI deployment
