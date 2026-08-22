# Implementation Log: Exchange Rate Management System

**Purpose**: A running record of what's been built, why, and every non-obvious bug found and
fixed along the way — the context a fresh session needs that `tasks.md`'s checkboxes alone can't
carry. Update this file (append, don't rewrite history) at the end of each future work session.

**Status as of 2026-08-23**: Backend Stages 1–3 complete (T001–T028). Branch
`feature/api-endpoints`, all work committed through `2ecd8bd`. 20/20 automated backend tests
passing. Frontend: scaffolded only (T004), no feature components yet. AI insight: not started
(Stage 4, T033+).

**Environment note**: Spring Boot **4.1.0** is genuinely in use (verified against real Maven
Central artifacts, not a corrupted local cache) and reorganized several test-support APIs out of
their pre-4.x locations — see "Spring Boot 4.x relocations" below before assuming a class lives
where it used to.

---

## Stage 1 — Data Models & Database (T001–T012)

- **T001–T004 (Setup)**: `pom.xml` given a real Spring Boot parent (`4.1.0`), `application.yml`
  created (H2 file datasource, `fixer.api-key`/`fixer.base-url` from env vars, no hardcoded key,
  Ollama model property), `OpenApiConfig` added, Angular scaffolded fresh via `ng new` (no SSR,
  Vitest, SCSS, `--file-name-style-guide=2016` to match the component-naming tasks.md already
  expects for Stage 5).
- **T005/T006**: `ExchangeRate` and `CurrencyUsage` JPA entities. Both verified by actually
  generating DDL via `ddl-auto=create` and diffing against `data-model.md`'s documented schema —
  matched exactly, including the unique constraint and both CHECK constraints.
- **T007**: `CurrencySpread` — Appendix B lookup. **Design decision, confirmed with user**: `EUR`
  is hardcoded as the base currency (Fixer.io free-tier is contractually fixed to EUR), not made
  configurable — deliberate simplicity choice.
- **T008**: `CurrencyCode` — a curated ~42-code set (not the full ISO 4217 list, per explicit
  instruction), guaranteed to cover every Appendix B code and the EUR/PLN worked example.
- **T009**: `ExchangeRateRepository` derived queries — no issues.
- **T010**: `ExchangeRateRepository#upsert`. **Dialect decision, confirmed with user**: H2's
  shorthand `MERGE INTO ... KEY(...) VALUES (...)`, not portable to Postgres — accepted since H2
  is the only datasource this assessment exercises. `created_at` preserved via a `COALESCE`
  subquery on update; `updated_at` always refreshed.
- **T011**: `CurrencyUsageRepository#incrementUsage` — **this took three attempts**, all proven
  by an actual 50-concurrent-thread test, not by inspection:
  1. H2 shorthand MERGE with the increment computed via a scalar subquery in `VALUES` → **lost
     updates** (50 concurrent calls → final count 8). The subquery reads the row's current value
     via an independent, unlocked read before the write.
  2. ANSI-standard `MERGE ... WHEN MATCHED THEN UPDATE SET query_count = query_count + 1 ...
     WHEN NOT MATCHED THEN INSERT ...` → matched/update branch is genuinely atomic, but the
     not-matched/insert branch still raced on a brand-new currency (9/50 threads hit
     `DataIntegrityViolationException`, final count 41).
  3. **Final**: a plain `UPDATE ... WHERE currency_code = ?` returning the affected-row count
     (`int`, not `void`) — provably atomic for an *existing* row. Row **creation** for a
     currency's first-ever lookup was deliberately pushed out of this method entirely, to the
     service layer (T024) — see there for why.
- **T012**: `ExchangeRateRepositoryTest` — proves the unique constraint is enforced by the schema,
  not just assumed. **Gotcha hit**: after a `saveAndFlush` throws (expected constraint
  violation), the Hibernate session is left unusable for further operations
  (`AssertionFailure: ... has a null identifier`) — fixed by injecting `EntityManager` and calling
  `.clear()` before the follow-up assertion. This is standard JPA behavior, not a bug in the test.

## Stage 2 — Scheduler & Fixer.io Integration (T013–T019)

- **T013**: `WebClientConfig` — `WebClient` bean backed by `JdkClientHttpConnector` (no
  Netty/reactor-netty — plan.md explicitly wants "not reactive end-to-end"), with an
  `ExchangeFilterFunction` that appends `access_key=<fixer.api-key>` to every request. Verified
  against a local echo `HttpServer`, not just by inspection.
- **T014**: `SchedulingConfig` (`@EnableScheduling`) — verified with a temporary `@Scheduled`
  probe that actually fired repeatedly.
- **T015**: `FixerClient#fetchLatestRates` — maps Fixer.io's `/latest` response (including its
  own `success: false` in-body error convention, which returns **HTTP 200**, not a 4xx/5xx — must
  be checked explicitly). All 4 failure paths (unreachable, HTTP 500, `success:false`, empty body)
  tested against a mock server; success path confirmed `date` comes from the response body, never
  `LocalDate.now()`, and rates deserialize as real `BigDecimal` (no precision loss).
- **T016**: `RateIngestionService#ingestLatestRates` — single `@Transactional` method (fetch +
  whole upsert loop). **Proved, not assumed**: seeded prior data, then fed a batch where one
  currency (after another had already been successfully upserted) violated the `rate_to_usd > 0`
  check constraint — the *already-processed* currency's upsert rolled back too. Prior data stayed
  untouched throughout.
- **T017**: `RateIngestionScheduler` — `@Scheduled(cron = "0 5 0 * * *", zone = "GMT")`. Verified
  the cron expression's real next-fire-time (`2026-03-16T00:05Z` from an arbitrary starting
  point), and that a failing run is caught/logged and doesn't propagate (proven by pointing at an
  unreachable host and calling the method directly).
- **T018/T019**: `RateIngestionServiceTest` — idempotency (two calls, same day, exactly one row)
  and rollback (mocked `FixerClient` failure leaves *pre-existing* rows byte-for-byte unchanged,
  including audit timestamps — not just "table stays empty").

## Stage 3 — API Endpoints (T020–T028)

- **T020/T021**: `ApiException` hierarchy (`UnknownCurrencyException`→400,
  `RateNotAvailableException`→404, `UpstreamFetchException`→502) + `GlobalExceptionHandler`.
  Also handles generic Spring MVC failures (`MethodArgumentTypeMismatchException`→400
  `INVALID_DATE_FORMAT`, `MissingServletRequestParameterException`, `MethodArgumentNotValidException`,
  `HttpMessageNotReadableException`) plus a catch-all→500. Required adding
  `spring-boot-starter-validation` — without a real Bean Validation provider, `@Valid` is a no-op
  and `MethodArgumentNotValidException` could never fire. **Known open item**: validation messages
  come back in the JVM's default locale (`ru_BY` on this machine) — the `error` code is
  unaffected, only the human-readable `message`. Not fixed, just flagged.
- **T022/T023**: `SpreadCalculationService` — pure formula + a currency-code overload. **Design
  decision, confirmed with user**: same-currency pairs short-circuit to exactly `1` *before* any
  spread lookup — the formula alone would not produce `1` for same-currency if that currency's
  real spread is non-zero. 10 tests: every Appendix B tier, the pinned EUR/PLN worked example
  (**using the brief's illustrative 1%/4% spreads, not the real EUR/PLN Appendix B tiers** — EUR
  is base=0%, so routing this test through `CurrencySpread` would silently produce the wrong
  answer), same-currency, tied spreads, null-code fail-fast, unrecognized-but-non-null fallback.
- **T024/T025**: `UsageTrackingService#recordLookup(from, to, date)` — **two real bugs found by
  running the concurrency test, not by review**:
  1. Off-by-one: creating a currency's first row via `save(new CurrencyUsage(...))` left
     `query_count = 0` (the constructor's default) — the triggering lookup itself was never
     counted. Caught by two simple *non-concurrent* test failures first.
  2. Non-deterministic under 50-thread concurrency (9, then 20, then 23 — never 50): mixing an
     ORM-managed `save()`/`saveAndFlush()` with a native `@Modifying` bulk query in the *same*
     transaction. Root-caused via direct atomic-counter instrumentation to Hibernate's
     first-level session cache holding a stale view of a row a native query had changed
     underneath it (14 "successful inserts" were counted for one PK-constrained currency — proof
     something was fundamentally wrong, not just imprecise).
  **Fix**: added `CurrencyUsageRepository#insertNewRow` — a second **pure native** INSERT
  setting `query_count = 1` directly. Both write paths (`incrementUsage`, `insertNewRow`) are now
  100% native SQL; no entity is ever attached to a persistence context on this path. Verified with
  5 repeated full reruns post-fix, all exactly 50/50.
  Retry mechanics: up to 3 attempts, each in its own transaction via `TransactionTemplate` +
  `PROPAGATION_REQUIRES_NEW` — **deliberately not** `@Transactional(REQUIRES_NEW)` on a
  same-class helper (Spring AOP doesn't intercept self-invocation; that would silently collapse
  everything into one shared transaction).
  Public signature matches tasks.md exactly: `recordLookup(String fromCurrency, String
  toCurrency, LocalDate queriedDate)` — the retry-bearing single-currency logic is a *private*
  helper, not a second public overload (an earlier draft added one; reverted to match the spec
  after the user asked to follow it exactly).
- **T026**: Three DTOs (`ExchangeRateResponse`, `HistoricalRatePoint`, `HistoryResponse`) as
  records. Verified actual JSON output byte-for-byte against contracts/exchange.md's literal
  example (confirms Spring Boot's default Jackson config serializes `LocalDate` as plain ISO and
  `BigDecimal` as a plain non-scientific-notation number with zero extra annotations needed).
- **T027/T028**: `ExchangeRateController` — all three endpoints in one class (T028 explicitly
  allowed either; merged for simplicity). **Design decision, confirmed with user**: same-currency
  pairs always return `exchange: 1` with **zero DB lookup**, regardless of whether that currency
  has any stored data at all (matches spec.md's literal "rather than an error" wording). Added
  `InvalidDateRangeException` (400 `INVALID_DATE_RANGE`) for `startDate` after `endDate` — none
  of the three T020 exceptions fit that case. Every documented scenario verified manually over
  real HTTP against seeded data, **including a genuine call to the real Fixer.io API** (got back
  a real `invalid_access_key` response, correctly mapped to 502) and a mock-server success path
  for `/refresh`.

---

## Cross-Cutting Facts Worth Remembering

- **Package/groupId**: `com.exchange.exchangeratesystem` / Maven `com.exchange:exchange-rate-system`
  — the copied planning docs originally said `com.marcura...`; this was corrected during the
  Russian-text/Marcura-removal pass before implementation started.
- **Datasource**: H2, file-based for dev (`jdbc:h2:file:./data/exchangedb`), always overridden to
  an isolated in-memory instance in tests via `@SpringBootTest(properties = {...})`.
- **Verification pattern used throughout**: for anything with real runtime behavior (schema
  generation, HTTP responses, concurrency), a temporary scratch class was written outside the
  main tree (or added and then deleted), the app was booted with env-var overrides
  (`FIXER_API_KEY`, `FIXER_BASE_URL`, `SPRING_JPA_HIBERNATE_DDL_AUTO`), exercised via `curl`/a
  `CommandLineRunner`, then torn down — never left in the committed tree. `backend/data/` (the
  local H2 file) was cleaned up after every such run.
- **Spring Boot 4.x relocations actually hit this session** (verify before assuming a 3.x-era
  location is still correct):
  - `@DataJpaTest` moved from `spring-boot-test-autoconfigure`
    (`org.springframework.boot.test.autoconfigure.orm.jpa`) to a new dedicated module
    `spring-boot-data-jpa-test` (`org.springframework.boot.data.jpa.test.autoconfigure`). Not
    pulled in transitively by `spring-boot-starter-test` — must be a separate dependency.
  - `@MockBean` → `@MockitoBean`, which moved **out of Spring Boot entirely** into core Spring
    Framework's new bean-override mechanism:
    `org.springframework.test.context.bean.override.mockito.MockitoBean` (in `spring-test`,
    already transitively present — no new dependency needed).
  - `@SpringBootTest` itself did **not** move (an earlier scan that suggested otherwise was
    incomplete, not a real finding).
- **Commit convention**: every commit prefixed `[AI]` (constitution's AI-Augmented Workflow
  requirement); scope-style suffix per task area (`config:`, `scheduler:`, `service:`, `test:`,
  `error:`, `controller:`, `dto:`, `usage:`, etc.).
- **`tasks.md` checkboxes**: T001–T028 marked `[X]` as of this log entry. Keep this in sync going
  forward — it had drifted stale (all unchecked despite being done) partway through this session.

## Open Items / Known Gaps (carried into next session)

1. **T029–T032 not started**: `AnalyticsResponse`/`AnalyticsController` (T029), the still-missing
   `ExchangeRateControllerIT` integration test (T030 — the biggest real gap: `ExchangeRateController`
   has zero *automated* tests right now, only manual `curl` verification from this session), a
   `DevDataSeeder` (T031), and a Swagger-annotation review pass (T032).
2. **Locale-dependent validation messages** (see T020/T021 above) — not fixed, just documented.
3. **`mostRecentCommonDate`'s divergent-history edge case is untested** — takes `MIN` of both
   currencies' latest dates when no `date` param is given; correct in the normal case (all
   currencies ingested together) but no test currently seeds a genuinely divergent history to
   exercise the fallback.
4. **Frontend is still just the bare `ng new` scaffold** (T004) — no feature components, no
   `core/` services, nothing wired to the backend yet. That's all of Stage 5.
5. **AI insight (Stage 4, T033–T041) not started at all.**
