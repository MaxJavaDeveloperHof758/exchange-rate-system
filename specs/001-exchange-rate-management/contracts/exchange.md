# API Contract: Exchange Rate Endpoints

Backs User Story 1 (Calculator) and User Story 2 (Historical Rates & Trend Chart, raw data half).
See [data-model.md](../data-model.md) for the underlying entities and
[spec.md](../spec.md) for FR-005–FR-010, FR-014.

## `GET /api/exchange`

Returns the spread-adjusted rate for a currency pair, using only locally stored data.

**Query Parameters**:

| Name | Type | Required | Notes |
|---|---|---|---|
| `from` | string (3-letter currency code) | Yes | Source currency. |
| `to` | string (3-letter currency code) | Yes | Target currency. |
| `date` | string (`YYYY-MM-DD`) | No | If omitted, the most recent date with stored data for both currencies is used (FR-008). |

**Success — `200 OK`** (shape matches brief Appendix A exactly):

```json
{
  "from": "EUR",
  "to": "PLN",
  "exchange": 4.4405487565413254,
  "date": "2024-03-15",
  "fromQueryCount": 142,
  "toQueryCount": 37
}
```

`fromQueryCount`/`toQueryCount` are the *post-increment* running totals for each currency,
reflecting the increment this successful call itself just performed (FR-011).

**Errors**:

| Status | Condition | Body |
|---|---|---|
| `400 Bad Request` | `from`/`to` is not a recognized currency code, or `date` is malformed (FR-010) | `{ "error": "UNKNOWN_CURRENCY", "message": "..." }` or `{ "error": "INVALID_DATE_FORMAT", "message": "..." }` |
| `404 Not Found` | No stored rate exists for one or both currencies on the resolved date (FR-009) | `{ "error": "RATE_NOT_AVAILABLE", "message": "No rate data available for EUR/PLN on 2024-03-15" }` |

**Behavioral notes**:
- Same-currency pairs (e.g. `from=EUR&to=EUR`) return `exchange: 1` with no spread applied
  (spec.md User Story 1, Acceptance Scenario 4) and still increment the usage counter (once,
  since it is the same currency on both sides — see `usage.md` note in `analytics.md`).
- The usage-counter increment (FR-011, FR-012) MUST occur only after the rate has been
  successfully resolved — a `400`/`404` response MUST NOT increment any counter.

---

## `GET /api/exchange/history`

Returns the raw, stored rates for a currency pair over a date range (User Story 2's table +
chart data source).

**Query Parameters**:

| Name | Type | Required | Notes |
|---|---|---|---|
| `from` | string (3-letter currency code) | Yes | Source currency. |
| `to` | string (3-letter currency code) | Yes | Target currency. |
| `startDate` | string (`YYYY-MM-DD`) | Yes | Inclusive start of range. |
| `endDate` | string (`YYYY-MM-DD`) | Yes | Inclusive end of range. |

**Success — `200 OK`**:

```json
{
  "from": "EUR",
  "to": "PLN",
  "startDate": "2024-03-01",
  "endDate": "2024-03-15",
  "points": [
    { "date": "2024-03-01", "fromRateToUsd": 0.80, "toRateToUsd": 3.70, "adjustedRate": 4.4978125 },
    { "date": "2024-03-02", "fromRateToUsd": 0.81, "toRateToUsd": 3.72, "adjustedRate": 4.4590123457 }
  ],
  "missingDates": ["2024-03-03"]
}
```

`points` contains only dates where a spread-adjusted rate could actually be computed for the
pair; `missingDates` explicitly lists any date within `[startDate, endDate]` that had no usable
stored data for one or both currencies (spec.md User Story 2, Acceptance Scenario 5) — the
response is never a hard failure just because part of the range is incomplete.

`fromRateToUsd`/`toRateToUsd` are each currency's own raw stored rate-to-USD for that date
(FR-014: "the raw rates that are actually stored," not just the derived pair rate) — `null` for
a same-currency pair, whose `adjustedRate` is always exactly `1` with no database lookup at all.
`adjustedRate` is the spread-adjusted pair rate — the field this endpoint originally called
`exchange` before both raw rates were added alongside it.

**Errors**:

| Status | Condition | Body |
|---|---|---|
| `400 Bad Request` | Unknown currency code, malformed date, or `startDate` after `endDate` | `{ "error": "...", "message": "..." }` |
| `404 Not Found` | Zero usable data points exist anywhere in the requested range | `{ "error": "RATE_NOT_AVAILABLE", "message": "No rate data available for EUR/PLN between 2024-03-01 and 2024-03-15" }` |

**Behavioral note**: This endpoint does not increment usage counters — usage tracking (FR-011)
is scoped to `/api/exchange` single-pair lookups only, per spec.md's Assumptions ("usage" is
defined around the calculator-style lookup, not every read of historical data).

---

## `POST /api/exchange/refresh` (Optional — FR-022)

Manually triggers an out-of-schedule ingestion run, reusing the exact same upsert path as the
daily scheduled job (research.md Decision 3 and Decision 6).

**Request body**: none.

**Success — `202 Accepted`**:

```json
{
  "triggeredAt": "2026-08-21T10:15:00Z",
  "status": "COMPLETED"
}
```

**Errors**:

| Status | Condition | Body |
|---|---|---|
| `502 Bad Gateway` | The upstream provider (Fixer.io) call failed | `{ "error": "UPSTREAM_FETCH_FAILED", "message": "..." }` |

**Behavioral note**: This endpoint MUST NOT read or write any `Currency Usage Counter` row under
any outcome (brief Section 4.4).
