# QuoteFlow Reporting & Tenant Dashboard

## Status

**IMPLEMENTED (Phase 10)** — read-only tenant Business Overview.

Not a P&L, balance sheet, cash-flow statement, GST return, or bookkeeping ledger.

## Architecture

```text
Quotations / Invoices / Payments / Customers (authoritative)
        ↓
PostgreSQL aggregate SQL (ReportingRepository)
        ↓
ReportingService (period + timezone defaults)
        ↓
GET /api/v1/dashboard/summary
        ↓
Angular dashboard (display only)
```

Reporting never mutates domain tables and never becomes source of truth for balances.
Payment state for SENT invoices uses the same rules as Phase 9 (`RECORDED` sums vs invoice total).

## Endpoint

`GET /api/v1/dashboard/summary?from=&to=`

- Auth required; tenant from JWT `businessId`
- Omit both `from` and `to` → **current calendar month in Business timezone**
- Provide both → inclusive ISO `LocalDate` range (`from <= to`)
- Invalid range → `400 INVALID_DATE_RANGE`
- Max span: 3660 days (~10 years)

One consolidated response covers cards, status counts, collections series, and recent activity.
No separate `/reports` routes in Phase 10.

## Metric definitions

| Metric | Definition |
|--------|------------|
| **Invoiced** | `SUM(total_amount)` of **SENT** invoices with `issue_date` in `[from,to]`, grouped by currency |
| **Collected** | `SUM(amount)` of **RECORDED** payments with `payment_date` in `[from,to]`, grouped by currency |
| **Outstanding now** | Current `total − RECORDED payments` for **SENT** invoices with `issue_date` in period, by currency |
| **UNPAID / PARTIALLY_PAID / PAID** | Counts of SENT invoices in period by Phase 9 payment-state rules |
| **Draft / Cancelled invoice counts** | Status counts by `issue_date` in period; **not** included in invoiced/outstanding money |
| **Quotation counts** | By `issue_date` in period; quoted amount = DRAFT+SENT totals by currency (never added to invoiced) |
| **Converted** | SENT quotations in period that have a linked invoice (`source_quotation_id`) |
| **Customers** | Active/archived all-time; `newInPeriodCount` uses `created_at` converted to Business timezone date |

## Date & timezone policy

- Invoice period filter: `invoices.issue_date` (business calendar date)
- Collections period filter: `payments.payment_date` (business calendar date), **not** `created_at`
- Quotation period filter: `quotations.issue_date`
- Default “this month”: `LocalDate.now(Business.timezone)` → YearMonth bounds
- Inclusive: day before / after the range are excluded
- **No historical as-of accounting** (e.g. “balance as of 31 Mar”) in Phase 10

## Cancelled & voided

- CANCELLED invoices excluded from invoiced/outstanding money
- VOIDED payments contribute **0** to collected and to payment-state paid amounts

## Mixed currency

Financial fields are `MoneyByCurrency[]`. **Never sum INR+USD.** No FX conversion.

## Collections series

- Range ≤ 92 days → daily buckets
- Longer → monthly buckets
- Only RECORDED payments; `payment_date` based

## Recent activity

Latest 5 invoices and 5 RECORDED payments for the tenant (not period-filtered). Safe summary fields only (no payment notes).

## Indexes

Existing Phase 6–9 indexes cover reporting filters (`business_id` + `issue_date` / `payment_date` / `status`). Phase 11 added `(business_id, created_at)` for quota counts — reporting still uses issue/payment dates.

Dashboard metrics are **not** filtered by SaaS plan. Platform MRR/ARR must never appear on the tenant Business Overview.

## Privacy & security

- Tenant isolation mandatory; aggregates never cross tenants
- Parameterized SQL only
- No PII in metric labels / Actuator metrics
- Dashboard is **tenant** overview only — not QuoteFlow platform MRR/ARR

## Future scale (not implemented)

PostgreSQL aggregates → measured read models → warehouse only at substantial scale.

## Known limitations

- Outstanding is **current** balance for invoices issued in the period, not as-of a past date
- Client period presets (last 30 / this year) send explicit dates from the browser calendar; default “this month” is server/Business-timezone authoritative
- No CSV/PDF export
