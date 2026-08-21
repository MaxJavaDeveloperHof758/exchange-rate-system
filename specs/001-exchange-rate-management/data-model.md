# Phase 1 Data Model: Exchange Rate Management System

**Input**: [spec.md](spec.md) Key Entities, [research.md](research.md) Decisions 3 and 5

## Entity: Exchange Rate

Represents one currency's rate for one specific validity date, as reported by the upstream
provider (Fixer.io).

| Field | Type | Notes |
|---|---|---|
| `id` | Long (surrogate PK) | Auto-generated; not exposed in API responses. |
| `currencyCode` | String(3) | ISO 4217 currency code, e.g. `EUR`, `PLN`. |
| `rateToUsd` | `BigDecimal` | The currency's rate relative to the reference currency returned by the provider, stored at full provider precision (see Database Schema for scale). MUST NOT be `double`/`float` (constitution Principle I). |
| `rateDate` | `LocalDate` | The date the rate is valid for, **as reported by the provider's response payload** — never the system clock date of the fetch (FR-002). |
| `createdAt` | `Instant` | Audit timestamp, set on first insert. |
| `updatedAt` | `Instant` | Audit timestamp, refreshed on every upsert (including no-op re-fetches of unchanged data). |

**Constraints**:
- Unique on `(currencyCode, rateDate)` — enforced at the database level (research.md Decision 3),
  not only in application code. This is the mechanism that makes FR-003/FR-004 (no duplicate rows
  for a currency+date, safe under multi-instance re-ingestion) hold.
- `rateToUsd` MUST be non-null and positive.

**Lifecycle**: Written only by the daily ingestion process (or the optional manual-refresh
trigger, FR-022) as an upsert; never created or mutated by a read-path (Calculator, Historical
view) request. Read-only from every other component's perspective (per spec.md Assumptions).

## Entity: Currency Usage Counter

Represents the running count of successful lookups a given currency has participated in, on
either side of the pair.

| Field | Type | Notes |
|---|---|---|
| `currencyCode` | String(3) (PK) | ISO 4217 currency code — one row per currency ever queried. |
| `queryCount` | Long | Monotonically increasing count, incremented atomically (research.md Decision 5). |
| `lastQueriedDate` | `LocalDate` | The date of the most recent successful query involving this currency. |

**Constraints**:
- `queryCount` MUST only ever increase, by exactly 1 per successful lookup that involves this
  currency (FR-011), and MUST NOT decrease or be reset by any endpoint other than a
  deliberately-scoped administrative action (none is required by this feature).
- The manual-refresh trigger (FR-022) MUST NOT write to this entity at all (brief Section 4.4).

**Lifecycle**: A row is created (count = 1) on the first successful lookup involving that
currency, and incremented on every subsequent successful lookup involving it — one row update per
currency per successful `/exchange` request (a EUR→PLN lookup touches two rows: EUR and PLN).

## Reference Table: Currency Spread (Not Persisted Per-Fetch)

A fixed, static lookup — not a database table populated by ingestion, and not versioned per rate
fetch — mapping a currency code to its spread percentage. Defined once in application
configuration/code per brief Appendix B:

| Currency Group | Spread % |
|---|---|
| Base currency (as returned by the Fixer.io API key) | 0.00% |
| JPY, HKD, KRW | 3.25% |
| MYR, INR, MXN | 4.50% |
| RUB, CNY, ZAR | 6.00% |
| All other currencies | 2.75% |

**Rationale for not persisting this as a row-per-fetch table**: The spread table is a fixed
business rule from the brief, not observed data from the provider — it does not vary by date, so
storing it once (in configuration or an enum-backed lookup) rather than duplicating it into every
`Exchange Rate` row keeps the calculation logic and the stored data cleanly separated
(constitution Principle V), and avoids a class of data-integrity bugs where a spread value could
drift between rows for the same currency.

## Entity: Trend Insight (Ephemeral, Not Persisted)

Represents one AI-generated natural-language commentary for a specific currency pair and date
range.

| Field | Type | Notes |
|---|---|---|
| `fromCurrency` | String(3) | Echoed from the request. |
| `toCurrency` | String(3) | Echoed from the request. |
| `fromDate` | `LocalDate` | Echoed from the request — start of the requested range. |
| `toDate` | `LocalDate` | Echoed from the request — end of the requested range. |
| `insight` | String | The generated commentary text (FR-015, FR-016). |

**Lifecycle**: Computed on demand per request; not stored. Regenerating the same range MAY yield a
differently-worded (but data-consistent) result, since the underlying model call is not
guaranteed deterministic — this is acceptable per spec.md's Assumptions (best-effort, qualitative
commentary).

## Relationships

- `Exchange Rate` has no foreign-key relationship to `Currency Usage Counter` — they are both
  keyed independently by `currencyCode` but serve different purposes (stored market data vs.
  observed usage) and are never joined at the database level; they are combined only in the
  `/exchange` response DTO at the service layer.
- `Trend Insight` is derived at request time from a range query over `Exchange Rate` for the two
  requested currencies; it has no persisted relationship to any entity.
- `Currency Spread` is looked up (not joined) by `currencyCode` during the calculation step used
  by both the `/exchange` and `/exchange/history`-adjacent calculation logic.

## Database Schema

See [contracts/](contracts/) for the API shapes these entities back. Concrete column-level types,
precision/scale, and indexes are defined below; this is the schema the JPA entities above must
map onto.

### `exchange_rate`

| Column | Type | Constraints |
|---|---|---|
| `id` | `BIGINT` | `PRIMARY KEY`, auto-increment / identity |
| `currency_code` | `VARCHAR(3)` | `NOT NULL` |
| `rate_to_usd` | `DECIMAL(19, 10)` | `NOT NULL`, `CHECK (rate_to_usd > 0)` |
| `rate_date` | `DATE` | `NOT NULL` |
| `created_at` | `TIMESTAMP` | `NOT NULL` |
| `updated_at` | `TIMESTAMP` | `NOT NULL` |

- `UNIQUE (currency_code, rate_date)` — backs research.md Decision 3's native upsert and FR-003/
  FR-004.
- `INDEX (currency_code, rate_date)` — the unique constraint above already provides this
  composite index for the primary lookup pattern (single pair + date, or single pair + most
  recent date via `ORDER BY rate_date DESC LIMIT 1`) and for the historical range query
  (`WHERE currency_code = ? AND rate_date BETWEEN ? AND ? ORDER BY rate_date ASC`) without a
  separate index.

### `currency_usage`

| Column | Type | Constraints |
|---|---|---|
| `currency_code` | `VARCHAR(3)` | `PRIMARY KEY` |
| `query_count` | `BIGINT` | `NOT NULL`, `DEFAULT 0`, `CHECK (query_count >= 0)` |
| `last_queried_date` | `DATE` | `NOT NULL` |

- No additional index needed: the analytics query (`SELECT * FROM currency_usage ORDER BY
  query_count DESC`) is a full-table scan over what is, by definition, a small table (one row per
  distinct currency ever queried — at most a few hundred rows for any real currency universe),
  so a secondary index on `query_count` would add write overhead to the hot atomic-increment path
  for no measurable read benefit at this scale.

### Precision Rationale

`DECIMAL(19, 10)` gives 9 integer digits and 10 fractional digits — comfortably wide enough for
any real-world currency-to-USD rate (including very small rates like JPY-per-USD-inverted
representations) while keeping enough fractional precision that the intermediate division in the
spread formula (research.md; constitution Principle I) does not lose meaningful information
before the final response-level rounding is applied.
