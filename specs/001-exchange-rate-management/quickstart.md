# Quickstart: Exchange Rate Management System

Validates spec.md's SC-007 ("A reviewer can run the full system locally ... using only the
documented setup steps") end-to-end. This is a validation/run guide — implementation detail
belongs in `tasks.md` and the actual code, not here.

## Prerequisites

- Java 17+ (this project's `pom.xml` pins a specific JDK — see the root README for the exact
  version) and Maven.
- Node.js + npm (for `ng serve`), Angular CLI compatible with the version pinned in
  `frontend/package.json`.
- A Fixer.io free-tier API key (Section 2 of the brief) set as an environment variable — do not
  hardcode it in `application.yml`.
- Ollama installed locally, with the configured model pulled (research.md Decision 2), *or* an
  OpenAI-compatible endpoint configured as documented in the root README's "AI Workflow"/model
  setup section (brief Section 7.2, final bullet: "Document your model setup in the README").

## Step 1 — Start the backend

```bash
export FIXER_API_KEY=your-fixer-io-key
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run
```

The `dev` profile does two things (T031): enables schema auto-creation for the local file-based
H2 database (`spring.jpa.hibernate.ddl-auto=update`, `application-dev.yml`) and activates
`DevDataSeeder`, a `CommandLineRunner` that idempotently inserts the EUR/PLN worked-example rates
below on every startup — safe to restart repeatedly, never duplicates rows.

Expected: the application starts on its configured port with no startup errors, and
`/swagger-ui.html` (or the configured springdoc path) is reachable and lists every endpoint in
[contracts/](contracts/) (constitution Principle VI; SC-008).

## Step 2 — Confirm ingestion has data to serve

The scheduled job only fires once daily at 12:05 AM GMT (FR-001), so a fresh local run has no
data yet unless one of the following is true:
- `DevDataSeeder` (T031, active via the `dev` profile from Step 1) pre-populates today's date
  with the brief's EUR/PLN worked-example rates (Section 6.2: `0.80`/`3.70` rate-to-USD) so the
  system is immediately demoable, **or**
- The optional manual-refresh endpoint (`POST /api/exchange/refresh`, [contracts/exchange.md]
  (contracts/exchange.md)) is called once to pull real data from Fixer.io on demand.

Expected: `GET /api/exchange?from=EUR&to=PLN` returns `200 OK` with `exchange: 4.49781250000000000000`
against the worked-example data (or real Fixer.io data if refresh was used instead), matching
SC-001. This is *not* the brief's own illustrative `4.44` — that figure uses the brief's
illustrative 1%/4% spreads to demonstrate the formula (Section 6.2), not PLN's actual Appendix B
tier, which `CurrencySpread` (T007) correctly assigns to the 2.75% "all other currencies" default
rather than EUR's 0% base tier: `(3.70/0.80) × ((100−2.75)/100) = 4.625 × 0.9725 = 4.4978125`
(displayed at the formula's full 20-digit `BigDecimal` scale).

## Step 3 — Validate the missing-data and invalid-input error paths

```bash
curl "http://localhost:8080/api/exchange?from=EUR&to=PLN&date=1999-01-01"
curl "http://localhost:8080/api/exchange?from=XXX&to=PLN"
```

Expected: the first call returns `404` with a `RATE_NOT_AVAILABLE` body; the second returns `400`
with an `UNKNOWN_CURRENCY` body — matching [contracts/exchange.md](contracts/exchange.md) and
SC-002.

## Step 4 — Validate concurrency safety of usage counters

Fire at least 50 concurrent requests at the same currency pair (e.g. with a simple shell loop and
`&`, or an existing load tool) and then check `GET /api/analytics`.

Expected: the reported `totalCount` for each involved currency increases by exactly the number of
successful requests that involved it — zero lost or duplicated increments, matching NFR-002/
SC-003.

## Step 5 — Start the frontend

```bash
cd frontend
npm install
ng serve
```

The backend base URL is read from `frontend/src/environments/environment.development.ts` — set it
to match Step 1's backend port with no source-code change elsewhere (FR-019/NFR-007).

Expected: navigating to the served URL loads the app shell with three routes: `/calculator`,
`/trend`, `/analytics`.

## Step 6 — Walk each of the three required views

1. **Calculator** (`/calculator`): submit EUR → PLN. Expected: a loading indicator appears
   briefly, then the rate and both currencies' query counts render. Submitting an unsupported
   currency code shows a clear, distinct validation/error message (FR-020).
2. **Historical Rates & Trend Chart** (`/trend`): pick a pair and a date range covering the
   available data. Expected: the table and line chart render side by side from
   [contracts/exchange.md](contracts/exchange.md)'s `/api/exchange/history` response, and the AI
   insight panel shows its own loading state before rendering commentary sourced from
   [contracts/insight.md](contracts/insight.md) — stop Ollama and reload to confirm the insight
   panel shows a distinct error state without breaking the table/chart (NFR-005).
3. **Usage Analytics Dashboard** (`/analytics`): confirm the currencies queried in Steps 3–4 and
   6.1 appear, ranked by count, matching [contracts/analytics.md](contracts/analytics.md) and
   SC-006.

## Step 7 — Re-run ingestion idempotency check

Trigger `POST /api/exchange/refresh` twice in a row (or restart the app twice to simulate two
scheduled firings). Expected: the row count in the `exchange_rate` table for that day does not
grow between runs (SC-004), and `Currency Usage Counter` rows are completely untouched by either
call (brief Section 4.4).

## Success

All seven steps completing as described is equivalent to satisfying SC-001 through SC-008 in
[spec.md](spec.md) end-to-end.
