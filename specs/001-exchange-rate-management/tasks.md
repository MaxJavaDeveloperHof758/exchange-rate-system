---

description: "Task list for Exchange Rate Management System implementation"
---

# Tasks: Exchange Rate Management System

**Input**: Design documents from `/specs/001-exchange-rate-management/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Included — constitution Principle VII and spec.md both require unit coverage of the
spread calculation and at least one integration test, plus AI-assisted frontend component tests.

**Organization**: Per an explicit request, tasks are organized into five self-contained,
independently-verifiable **stages** rather than the default Spec Kit user-story phase
framing. Each stage still carries a `[US#]` label per task mapping it back to spec.md's User
Story 1 (Calculator), User Story 2 (Historical Trend + AI Insight), or User Story 3 (Analytics)
for traceability — but the top-level grouping and checkpoints are the five stages below.

## Format: `[ID] [P?] [US#] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[US#]**: US1 / US2 / US3, matching spec.md's priorities — omitted for Setup/Polish
- Backend package root: `src/main/java/com/exchange/exchangeratesystem/`
- Backend test root: `src/test/java/com/exchange/exchangeratesystem/`
- Frontend root: `frontend/`

---

## Phase 0: Setup (Shared Infrastructure)

- [X] T001 Confirm `pom.xml` build succeeds (`mvn -q compile`) and
      `src/main/java/com/exchange/exchangeratesystem/ExchangeRateSystemApplication.java` exists
      with a standard `@SpringBootApplication` entry point.
- [X] T002 [P] Add `src/main/resources/application.yml` with: server port, a relational-database
      datasource (per constitution's fixed Technology Stack — any relational DB, local/dev/test
      config only), a `fixer.api-key` / `fixer.base-url` property pair read from an environment
      variable (no hardcoded key), and a `spring.ai.ollama.chat.options.model` property per
      research.md Decision 2.
- [X] T003 [P] Create `src/main/java/com/exchange/exchangeratesystem/config/OpenApiConfig.java`
      with an `OpenAPI` bean setting title/description/version so Swagger UI has a meaningful
      landing page (constitution Principle VI; contracts/ endpoints will register under it).
- [X] T004 [P] Confirm `frontend/` scaffold builds (`npm install && ng build`) and strip any
      unused server-side-rendering scaffolding per research.md Decision 4 (`server.ts`,
      `main.server.ts`, `app.config.server.ts`, `app.routes.server.ts`, related
      `package.json`/`angular.json` entries) if present.

**Checkpoint**: Both projects build independently; no business logic, no entities yet.

---

## Stage 1: Data Models & Database

**Goal**: Every entity, repository, and DB-level constraint from data-model.md exists and is
independently verifiable via a repository-layer test — no service or controller logic yet.

**Independent Test**: A `@DataJpaTest` (or equivalent repository-slice test) can save an
`ExchangeRate` row, save a second row for the same `(currencyCode, rateDate)`, and observe a
constraint violation (or upsert, once T011's native query is used directly) confirming the unique
constraint from data-model.md is actually enforced by the database — not just assumed.

### Files to create

- [X] T005 [US1] [US2] Create JPA entity `ExchangeRate` in
      `src/main/java/com/exchange/exchangeratesystem/rate/ExchangeRate.java` per data-model.md:
      fields `id` (`Long`, surrogate PK), `currencyCode` (`String`, length 3),
      `rateToUsd` (`BigDecimal`, column definition `DECIMAL(19,10)`), `rateDate` (`LocalDate`),
      `createdAt`/`updatedAt` (`Instant`); table-level unique constraint on
      `(currency_code, rate_date)`.
- [X] T006 [P] [US1] [US3] Create JPA entity `CurrencyUsage` in
      `src/main/java/com/exchange/exchangeratesystem/usage/CurrencyUsage.java` per data-model.md:
      `currencyCode` (`String`, `@Id`), `queryCount` (`Long`, default 0), `lastQueriedDate`
      (`LocalDate`).
- [X] T007 [P] [US1] Create `src/main/java/com/exchange/exchangeratesystem/currency/CurrencySpread.java`
      encoding the fixed Appendix B lookup table as `BigDecimal` percentages: base currency
      0.00%, `JPY`/`HKD`/`KRW` 3.25%, `MYR`/`INR`/`MXN` 4.50%, `RUB`/`CNY`/`ZAR` 6.00%, all other
      currencies 2.75%. Expose `BigDecimal spreadFor(String currencyCode)`.
- [X] T008 [P] [US1] Create `src/main/java/com/exchange/exchangeratesystem/currency/CurrencyCode.java`
      exposing `boolean isSupported(String code)` for the fixed set of ISO 4217 codes this system
      recognizes (used later by controllers to reject unknown codes with `400`).
- [X] T009 [US1] [US2] Create `ExchangeRateRepository` in
      `src/main/java/com/exchange/exchangeratesystem/rate/ExchangeRateRepository.java` extending
      `JpaRepository<ExchangeRate, Long>` with named query methods:
      `Optional<ExchangeRate> findByCurrencyCodeAndRateDate(String currencyCode, LocalDate rateDate)`,
      `Optional<ExchangeRate> findTopByCurrencyCodeOrderByRateDateDesc(String currencyCode)`,
      `List<ExchangeRate> findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(String currencyCode, LocalDate start, LocalDate end)`.
      *(depends on T005)*
- [X] T010 [US2] Add a `@Modifying @Query` native upsert method to `ExchangeRateRepository`
      (research.md Decision 3) — `void upsert(String currencyCode, BigDecimal rateToUsd, LocalDate rateDate)`
      implemented as a single `MERGE`/`ON CONFLICT DO UPDATE` statement keyed on
      `(currency_code, rate_date)`, updating `rate_to_usd` and `updated_at` on conflict.
      *(depends on T005, T009)*
- [X] T011 [P] [US1] [US3] Create `CurrencyUsageRepository` in
      `src/main/java/com/exchange/exchangeratesystem/usage/CurrencyUsageRepository.java` with a
      single `@Modifying @Query` atomic upsert-and-increment method (research.md Decision 5) —
      `void incrementUsage(String currencyCode, LocalDate queriedDate)` implemented as
      `INSERT ... ON CONFLICT (currency_code) DO UPDATE SET query_count = query_count + 1, last_queried_date = ?`
      — plus a derived `List<CurrencyUsage> findAllByOrderByQueryCountDesc()` for the analytics
      read path. *(depends on T006)*
- [X] T012 [P] [US1] Unit/repository test in
      `src/test/java/com/exchange/exchangeratesystem/rate/ExchangeRateRepositoryTest.java`
      confirming the `(currency_code, rate_date)` unique constraint from data-model.md is
      enforced by the schema (attempt a duplicate direct insert outside T010's upsert path and
      assert a constraint violation). *(depends on T009)*

**Checkpoint**: Entities persist correctly; the unique constraint and both repositories' key
methods are proven with tests; no calculation or HTTP logic exists yet.

---

## Stage 2: Scheduler & Fixer.io Integration

**Goal**: The system can independently pull real data from Fixer.io — on a schedule and,
optionally, on demand — and store it idempotently, without any dependency on Stage 3's
calculation/API layer.

**Independent Test**: Running the ingestion service twice in a row (or via T017's optional
manual-refresh trigger, called twice) against the same day results in exactly one stored
`ExchangeRate` row per currency for that day (data-model.md, spec.md SC-004) — verifiable via
`ExchangeRateRepository` queries alone, with no controller involved yet.

### Files to create

- [X] T013 [US2] Create `src/main/java/com/exchange/exchangeratesystem/config/WebClientConfig.java`
      exposing a `WebClient` (or equivalent HTTP client) bean pre-configured with the Fixer.io
      base URL and API key from `application.yml` (T002).
- [X] T014 [P] [US2] Create `src/main/java/com/exchange/exchangeratesystem/config/SchedulingConfig.java`
      with `@EnableScheduling`.
- [X] T015 [US2] Create `FixerClient` in
      `src/main/java/com/exchange/exchangeratesystem/rate/FixerClient.java` calling Fixer.io's
      `/latest` endpoint via the T013 client — `FixerRatesResult fetchLatestRates()` mapping the
      response's `date` field and `rates` map into a plain result object; the returned date MUST
      come from the API response body, never `LocalDate.now()` (data-model.md, FR-002).
- [X] T016 [US2] Create `RateIngestionService` in
      `src/main/java/com/exchange/exchangeratesystem/rate/RateIngestionService.java` —
      `void ingestLatestRates()` calling `FixerClient`, then calling T010's native upsert once per
      returned currency inside a single transaction; on a `FixerClient` failure, the method MUST
      leave all existing `ExchangeRate` rows untouched and propagate/log the failure rather than
      partially writing (NFR-004). *(depends on T010, T015)*
- [X] T017 [US2] Create `RateIngestionScheduler` in
      `src/main/java/com/exchange/exchangeratesystem/rate/RateIngestionScheduler.java` with
      `@Scheduled(cron = "0 5 0 * * *", zone = "GMT")` calling `RateIngestionService#ingestLatestRates`
      (brief Section 4.1: 12:05 AM GMT daily). *(depends on T016, T014)*
- [X] T018 [P] [US2] Unit test in
      `src/test/java/com/exchange/exchangeratesystem/rate/RateIngestionServiceTest.java` — mock
      `FixerClient` to return the same currency/date pair twice across two calls to
      `ingestLatestRates()`, and assert `ExchangeRateRepository` holds exactly one row for that
      pair afterward (validates FR-003/FR-004/NFR-003/SC-004). *(depends on T016)*
- [X] T019 [P] [US2] Unit test in
      `src/test/java/com/exchange/exchangeratesystem/rate/RateIngestionServiceTest.java` (same
      file, additional test method) — mock `FixerClient` to throw, call `ingestLatestRates()`,
      and assert previously-stored `ExchangeRate` rows are unchanged and no exception escapes
      uncaught in a way that would crash a scheduled-thread pool (NFR-004). *(depends on T016)*

**Checkpoint**: Ingestion is real, idempotent, and multi-run-safe, independent of any HTTP-facing
code — provable purely through repository-level assertions.

---

## Stage 3: API Endpoints

**Goal**: Every endpoint in [contracts/](contracts/) is implemented, documented in Swagger, and
returns the correct data and HTTP status codes — this is the stage that makes User Story 1 (P1)
fully demoable end-to-end via HTTP.

**Independent Test**: With Stage 1/2 data present (or the dev seed data below), `GET
/api/exchange?from=EUR&to=PLN` returns `200` with `exchange: 4.44` and increments both
currencies' counters (visible via `GET /api/analytics`); a missing date returns `404`; an unknown
currency returns `400`; `GET /api/exchange/history` and `POST /api/exchange/refresh` behave per
contracts/exchange.md — all independently curl-able with no frontend involved.

### Files to create

- [X] T020 [US1] Create `src/main/java/com/exchange/exchangeratesystem/error/ApiException.java`
      (base) and concrete subclasses `UnknownCurrencyException`, `RateNotAvailableException`,
      `UpstreamFetchException` in the same `error/` package.
- [X] T021 [US1] Create `GlobalExceptionHandler` (`@RestControllerAdvice`) in
      `src/main/java/com/exchange/exchangeratesystem/error/GlobalExceptionHandler.java` mapping:
      `UnknownCurrencyException` → `400`, `RateNotAvailableException` → `404`,
      `UpstreamFetchException` → `502`, matching the error bodies in contracts/exchange.md.
      *(depends on T020)*
- [X] T022 [US1] Create `SpreadCalculationService` in
      `src/main/java/com/exchange/exchangeratesystem/rate/SpreadCalculationService.java` —
      `BigDecimal calculate(BigDecimal toRateToUsd, BigDecimal fromRateToUsd, BigDecimal toSpread, BigDecimal fromSpread)`
      implementing exactly `(toRate/fromRate) × ((100 − MAX(toSpread, fromSpread))/100)` using
      `BigDecimal.divide(divisor, scale, RoundingMode.HALF_UP)` at every division (constitution
      Principle I/II). *(depends on T007)*
- [X] T023 [P] [US1] Unit test in
      `src/test/java/com/exchange/exchangeratesystem/rate/SpreadCalculationServiceTest.java`
      covering every Appendix B tier (base/JPY-group/MYR-group/RUB-group/other), the pinned
      EUR/PLN worked example (expect exactly 4.44), the same-currency edge case (rate 1, spread
      0), and a tie case where both spreads are equal. *(depends on T022)*
- [X] T024 [US1] [US3] Create `UsageTrackingService` in
      `src/main/java/com/exchange/exchangeratesystem/usage/UsageTrackingService.java` —
      `void recordLookup(String fromCurrency, String toCurrency, LocalDate queriedDate)` calling
      T011's atomic increment once per currency (twice total, or once if `fromCurrency` equals
      `toCurrency`). *(depends on T011)*
- [X] T025 [P] [US1] [US3] Concurrency test in
      `src/test/java/com/exchange/exchangeratesystem/usage/UsageTrackingServiceTest.java` firing
      at least 50 concurrent calls to `recordLookup` for the same currency (e.g. via an
      `ExecutorService` + `CountDownLatch`) and asserting the final `queryCount` equals exactly 50
      (validates NFR-002/SC-003). *(depends on T024)*
- [X] T026 [US1] [US2] Create DTOs in `src/main/java/com/exchange/exchangeratesystem/rate/dto/`:
      `ExchangeRateResponse` (`from`, `to`, `exchange`, `date`, `fromQueryCount`, `toQueryCount`
      per contracts/exchange.md) and `HistoricalRatePoint` (`date`, `exchange`) plus a
      `HistoryResponse` wrapper (`from`, `to`, `startDate`, `endDate`, `points`, `missingDates`).
- [X] T027 [US1] [US2] Create `ExchangeRateController` in
      `src/main/java/com/exchange/exchangeratesystem/web/ExchangeRateController.java` with:
      `GET /api/exchange` (`getExchangeRate`) — validates `from`/`to` via `CurrencyCode`, resolves
      the rate date (given, or most-recent via T009's `findTopByCurrencyCodeOrderByRateDateDesc`),
      calls `SpreadCalculationService`, calls `UsageTrackingService` only on success, returns
      `ExchangeRateResponse`; and `GET /api/exchange/history` (`getHistory`) — range query via
      T009, computing `missingDates` against the requested range per contracts/exchange.md.
      *(depends on T022, T024, T026, T009, T021)*
- [X] T028 [US2] Add `POST /api/exchange/refresh` (`refresh`) to `ExchangeRateController` (or a
      dedicated `AdminController` in the same `web` package) — synchronously calls
      `RateIngestionService#ingestLatestRates`, returns `202` with a timestamp per
      contracts/exchange.md, maps a `FixerClient` failure to `502` via `UpstreamFetchException`,
      and never calls `UsageTrackingService`. *(depends on T016, T021)*
- [X] T029 [P] [US3] Create `AnalyticsResponse` DTO in
      `src/main/java/com/exchange/exchangeratesystem/usage/dto/AnalyticsResponse.java`
      (`topCurrencies: List<CurrencyUsageEntry>`, each `currency`/`totalCount`/`lastQueried`) and
      `AnalyticsController` in
      `src/main/java/com/exchange/exchangeratesystem/web/AnalyticsController.java` exposing
      `GET /api/analytics` sorted by `totalCount` descending, per contracts/analytics.md.
      *(depends on T011)*
- [X] T030 [US1] Integration test in
      `src/test/java/com/exchange/exchangeratesystem/web/ExchangeRateControllerIT.java`
      (`@SpringBootTest`, real database) covering: success case with counter increment verified
      via a follow-up `/api/analytics` call, `404` on missing date, `400` on unknown currency
      code. *(depends on T027)*
- [X] T031 [P] Add a `dev`-profile-gated `CommandLineRunner` seeder in
      `src/main/java/com/exchange/exchangeratesystem/config/DevDataSeeder.java` inserting the
      EUR/PLN worked-example rows (0.8/3.7 rate-to-base) for today's date, so quickstart.md's
      steps are runnable without waiting on Fixer.io or the scheduler.
- [X] T032 [P] Review springdoc-openapi annotations across `ExchangeRateController` and
      `AnalyticsController` so Swagger UI documents every parameter, response shape, and error
      code exactly as specified in contracts/exchange.md and contracts/analytics.md.
      *(depends on T027, T029)*

**Checkpoint**: User Story 1 is fully demoable end-to-end via HTTP; User Story 3's data source
(`/api/analytics`) is real and correct; everything is independently curl-testable without a
frontend or the AI feature.

---

## Stage 4: AI Integration

**Goal**: `GET /api/exchange/insight` returns a genuinely data-grounded commentary per
contracts/insight.md, degrading gracefully when the local model is unavailable — independently
testable via HTTP without the frontend.

**Independent Test**: With at least two weeks of stored data for a pair, `GET
/api/exchange/insight?from=...&to=...&fromDate=...&toDate=...` returns `200` with an `insight`
string whose described direction matches the actual data movement (spec.md SC-005); stopping the
local model and repeating the call returns `503` with the `INSIGHT_UNAVAILABLE` body from
contracts/insight.md, without affecting `/api/exchange/history` for the same range.

### Files to create

- [X] T033 [US2] Add Spring AI's Ollama chat-model starter dependency wiring confirmation to
      `application.yml` (T002) — `spring.ai.ollama.base-url` and
      `spring.ai.ollama.chat.options.model` per research.md Decision 2; no code change beyond
      configuration if the starter auto-configures a `ChatClient` bean.
- [X] T034 [US2] Create `src/main/java/com/exchange/exchangeratesystem/error/InsightUnavailableException.java`
      and map it to `503` in `GlobalExceptionHandler` (extends Stage 3's T021 handler) per
      contracts/insight.md.
- [X] T035 [US2] Create `TrendInsightService` in
      `src/main/java/com/exchange/exchangeratesystem/insight/TrendInsightService.java` —
      `String generateInsight(String from, String to, LocalDate fromDate, LocalDate toDate)`:
      reads the same `(date, rate)` series `ExchangeRateController#getHistory` would return (via
      `ExchangeRateRepository`, T009), builds a system prompt constraining output to 2–4
      sentences with no generic filler and no financial-advice framing (constitution Principle
      X), injects the real series as user-message context, calls the Spring AI `ChatClient`, and
      throws `InsightUnavailableException` on any model-call failure. A single-day range MUST
      produce a single-observation phrasing, not a multi-day trend framing (spec.md User Story 2,
      Acceptance Scenario 6). *(depends on T009, T034)*
- [X] T036 [P] [US2] Unit test in
      `src/test/java/com/exchange/exchangeratesystem/insight/TrendInsightServiceTest.java` using a
      mocked `ChatClient`/`ChatModel` to assert: (a) the constructed prompt/user-message contains
      the actual injected rate values for the requested range, and (b) a `ChatClient` failure is
      translated into `InsightUnavailableException`, not an unhandled exception.
      *(depends on T035)*
- [X] T037 [US2] Create `InsightResponse` DTO in
      `src/main/java/com/exchange/exchangeratesystem/insight/dto/InsightResponse.java`
      (`from`, `to`, `fromDate`, `toDate`, `insight` per contracts/insight.md) and
      `InsightController` in
      `src/main/java/com/exchange/exchangeratesystem/web/InsightController.java` exposing
      `GET /api/exchange/insight` — `404` when no data exists in range (reusing T009's range
      query), `503` via T034 when the model call fails. *(depends on T035, T037's own DTO, T021)*
- [X] T038 [P] Extend Swagger/OpenAPI annotations to `InsightController` so it appears correctly
      documented in Swagger UI alongside Stage 3's endpoints. *(depends on T037)*

**Checkpoint**: User Story 2's AI half works end-to-end via HTTP, independent of the frontend, and
degrades to a clean `503` rather than a crash when Ollama is stopped.

---

## Stage 5: Frontend (Angular)

**Goal**: All three required Angular views exist, are routed, and consume the real backend
endpoints from Stages 3–4 through typed services — the point at which every user story becomes
demoable through the UI itself.

**Independent Test**: `ng serve` against a running backend from Stages 1–4; visiting
`/calculator`, `/trend`, and `/analytics` exercises User Stories 1, 2, and 3 respectively per
quickstart.md Step 6, with each view showing correct loading/error/success states.

### Files to create

- [X] T039 Create `frontend/src/environments/environment.ts` and
      `environment.development.ts`, each exporting `{ apiBaseUrl: string }` (dev value pointing
      at the local backend), and configure `frontend/angular.json`'s `fileReplacements` for the
      `development` configuration (FR-019/NFR-007).
- [X] T040 [P] Create shared Angular response models in `frontend/src/app/core/models/`:
      `exchange-rate.model.ts`, `history.model.ts`, `analytics.model.ts`, `insight.model.ts` —
      each matching the corresponding file in [contracts/](contracts/) field-for-field.
      *(depends on T039)*
- [X] T041 [P] Create the Angular app shell and routing in `frontend/src/app/app.routes.ts` and
      `frontend/src/app/app.config.ts`: three lazy standalone routes, `/calculator`, `/trend`,
      `/analytics`, plus a top-level nav shell component. *(depends on T039)*
- [X] T042 [P] [US1] Implement `ExchangeRateService` in
      `frontend/src/app/core/services/exchange-rate.service.ts` — `getRate(from, to, date?)`
      calling `GET /api/exchange` via `HttpClient`, typed with T040's model.
      *(depends on T040)*
- [X] T043 [US1] Implement `CalculatorComponent` (standalone) in
      `frontend/src/app/features/calculator/calculator.component.ts` (+ `.html`/`.scss`):
      reactive form (from/to/optional date), a loading state while the request is in flight, a
      distinct error state on `400`/`404` (contracts/exchange.md), a success state rendering the
      rate and both query counts. *(depends on T042, T041)*
- [X] T044 [P] [US1] Component test in
      `frontend/src/app/features/calculator/calculator.component.spec.ts` covering: invalid input
      is rejected before submit, loading indicator shows during the call, error message renders
      on a mocked `404`, success view renders on a mocked `200`. *(depends on T043)*
- [X] T045 [P] [US2] Implement `HistoryService` and `InsightService` in
      `frontend/src/app/core/services/history.service.ts` and `insight.service.ts` — one method
      each calling `GET /api/exchange/history` and `GET /api/exchange/insight` respectively, both
      typed with T040's models. *(depends on T040)*
- [X] T046 [US2] Implement `SvgLineChartComponent` in
      `frontend/src/app/features/historical-trend/svg-line-chart.component.ts`: a pure function
      mapping `{date, rate}[]` to an SVG `<polyline>` + axis ticks, no charting-library dependency
      (research.md Decision 1). *(depends on T041)*
- [X] T047 [P] [US2] Unit test in
      `frontend/src/app/features/historical-trend/svg-line-chart.component.spec.ts` testing the
      pure point-to-path mapping function directly (min/max scaling, empty-data case).
      *(depends on T046)*
- [X] T048 [US2] Implement `RateTableComponent` in
      `frontend/src/app/features/historical-trend/rate-table.component.ts` rendering the raw
      history points, with a visible indicator for any `missingDates` (contracts/exchange.md).
      *(depends on T045)*
- [X] T049 [US2] Implement `InsightPanelComponent` in
      `frontend/src/app/features/historical-trend/insight-panel.component.ts` with its own
      loading/error/success state, independent of the table/chart's state (FR-017).
      *(depends on T045)*
- [X] T050 [US2] Implement `HistoricalTrendComponent` in
      `frontend/src/app/features/historical-trend/historical-trend.component.ts` composing a
      pair+date-range picker with `RateTableComponent`, `SvgLineChartComponent`, and
      `InsightPanelComponent` side by side, firing the history and insight requests in parallel.
      *(depends on T046, T048, T049)*
- [X] T051 [P] [US3] Implement `AnalyticsService` in
      `frontend/src/app/core/services/analytics.service.ts` calling `GET /api/analytics`, typed
      with T040's model. *(depends on T040)*
- [X] T052 [US3] Implement `AnalyticsDashboardComponent` in
      `frontend/src/app/features/analytics-dashboard/analytics-dashboard.component.ts`: a ranked
      list/bar visualization of `topCurrencies` (reusing the no-dependency SVG approach from
      research.md Decision 1). *(depends on T051)*
- [X] T053 [P] [US3] Component test in
      `frontend/src/app/features/analytics-dashboard/analytics-dashboard.component.spec.ts`
      covering the empty-data state and a populated-ranking render. *(depends on T052)*

**Checkpoint**: All three user stories are fully demoable through the running Angular app against
the running backend.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T054 [P] Execute the full [quickstart.md](quickstart.md) validation manually end-to-end
      (backend start, seed/refresh, all three views via `ng serve`, the 50-request concurrency
      spot-check) and record/fix any discrepancy found.
- [X] T055 [P] Write the root `README.md`: local setup/run instructions (including the local AI
      model pull and the Fixer.io API key environment variable), an architecture overview, an
      **"AI Workflow"** section naming the tool(s) used, how they were configured, and at least
      one concrete example where AI-generated output was overridden or corrected and why (brief
      Section 8.2, item 3 — mandatory grading evidence).
- [X] T056 [P] Confirm the `.claude/` (or equivalent) AI tool configuration committed to the
      repository is substantive, not a placeholder — satisfying brief Section 8.2, item 2.
- [X] T057 Going forward, prefix commits that are primarily AI-generated/AI-assisted with `[AI]`
      (constitution's AI-Augmented Workflow Requirements; brief Section 8.2, item 4) — a process
      convention, no file change of its own.
- [X] T058 [P] Final review pass across `SpreadCalculationService`, `ExchangeRate`, and every DTO
      in the rate/insight path confirming no `double`/`float` appears anywhere in the money
      calculation or persistence path (constitution Principle I).

---

## Dependencies & Execution Order

### Stage Dependencies

- **Setup (Phase 0)**: No dependencies — start immediately.
- **Stage 1 (Data Models & Database)**: Depends on Setup. Blocks Stage 2 and Stage 3 (both need
  the entities/repositories to exist).
- **Stage 2 (Scheduler & Fixer.io)**: Depends on Stage 1 (T005, T009, T010). Independently
  verifiable on its own (see Stage 2's Independent Test) before Stage 3 exists.
- **Stage 3 (API Endpoints)**: Depends on Stage 1 fully, and on Stage 2's `RateIngestionService`
  only for the optional refresh endpoint (T028) — the core `/api/exchange` and `/api/exchange/
  history` endpoints (T027) only need Stage 1's repositories and can be built/tested with the
  T031 dev-seeder even before Stage 2 is complete, if parallelizing across contributors.
- **Stage 4 (AI Integration)**: Depends on Stage 1 (T009, for reading history) and reuses Stage
  3's `GlobalExceptionHandler` (T021) — does not depend on Stage 3's controllers otherwise.
- **Stage 5 (Frontend)**: Depends on Stages 3 and 4 being reachable over HTTP (a running backend);
  frontend scaffolding tasks (T039–T041) can start immediately in parallel with any backend stage.
- **Polish (Phase 6)**: Depends on all five stages being complete.

### User Story Coverage

- **User Story 1 (P1)**: Stage 1 (T005, T006, T007, T008, T009, T011) → Stage 3 (T020–T027, T030,
  T031, T032) → Stage 5 (T042, T043, T044). Independently demoable once Stage 3 is done, even via
  `curl` alone.
- **User Story 2 (P2)**: Stage 1 (T005, T009, T010) → Stage 2 (all) → Stage 3 (T026, T027, T028)
  → Stage 4 (all) → Stage 5 (T045–T050).
- **User Story 3 (P3)**: Stage 1 (T006, T011) → Stage 3 (T024, T029) → Stage 5 (T051, T052, T053).
  Fully independent of User Story 2's code paths.

### Parallel Opportunities

- Setup: T002, T003, T004 in parallel.
- Stage 1: T006, T007, T008 in parallel (distinct files); T011 in parallel with T009/T010.
- Stage 2: T014 in parallel with T013.
- Stage 3: T023 in parallel with T024/T025; T029 in parallel with T026/T027; T031/T032 in
  parallel with each other and with T030.
- Stage 4: T036 in parallel with T037/T038 once T035 lands.
- Stage 5: T040/T041 in parallel; T042/T045/T051 in parallel with each other (different services);
  T044/T047/T053 (tests) each run after their respective component.

---

## Implementation Strategy

### MVP First (Stages 1 → 2 → 3, User Story 1 only via HTTP)

1. Complete Phase 0: Setup.
2. Complete Stage 1: Data Models & Database.
3. Complete enough of Stage 3 (T020–T027, T030, T031) to make `GET /api/exchange` fully correct
   and tested — this alone satisfies User Story 1 end-to-end via `curl`/Swagger UI, without
   Stage 2's live ingestion (the T031 dev seeder stands in for real Fixer.io data).
4. **STOP and VALIDATE**: confirm the EUR/PLN dev-seeded data returns `4.49781250000000000000`
   (per Appendix B's real 2.75% PLN tier — see quickstart.md Step 2 for why this isn't the
   brief's illustrative `4.44`) and a missing date returns 404.

### Incremental Delivery (recommended order)

1. Setup + Stage 1 → both projects scaffolded, entities/repositories in place.
2. Stage 3 (core `/api/exchange` + `/api/exchange/history`) → User Story 1 demoable via HTTP.
3. Stage 2 → real, idempotent, scheduled ingestion replaces the dev seeder as the data source.
4. Stage 4 → User Story 2's AI half completes the historical-trend story via HTTP.
5. Stage 3's remaining analytics pieces (T029) → User Story 3's data source is real.
6. Stage 5 → all three views become demoable through the actual Angular UI.
7. Polish (Phase 6) → README/AI-Workflow write-up, full quickstart re-run, final BigDecimal audit.

### Parallel Team Strategy (if more than one contributor)

1. One contributor completes Setup + Stage 1 (blocks everyone else).
2. Then: Contributor A takes Stage 2 (scheduler/ingestion) and Stage 4 (AI) in sequence;
   Contributor B takes Stage 3 (API endpoints); Contributor C takes Stage 5 (frontend), starting
   with T039–T041 immediately and wiring real calls in as Stages 3/4 land.
3. Stages integrate at `ExchangeRateController` (Stage 3's T027 and Stage 2's T028) and at the
   frontend's `core/services/` layer (Stage 5) — coordinate those touch points explicitly.

---

## Notes

- [P] tasks touch different files and have no incomplete-task dependency.
- [US#] labels map every Stage 1–5 task to spec.md's US1/US2/US3 for traceability, per the
  explicit stage-based organization requested for this document.
- Tests are listed alongside their corresponding implementation task within each stage; run them
  and confirm they fail first if following strict TDD, per constitution Principle VII.
- Commit after each task or logical group; use the `[AI]` prefix (T057) for AI-assisted commits.
- Stop at any stage checkpoint to validate it independently before moving to the next.
