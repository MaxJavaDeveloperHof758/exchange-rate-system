# Post-Submission Adjustments Log

**Branch**: `fix/adjustments` | **Source**: a feedback document (PDF, 13 items) attached at the
start of this effort, covering both backend correctness/architecture concerns and a few
documentation/tooling notes. This file is the continuity record for that effort specifically —
`implementation-log.md` covers the original T001–T058 build; this covers what came after
submission.

**Established workflow (keep following it in any continuation session)**:
- One item at a time. Read the affected code fully before touching it.
- Before declaring a fix done: run the relevant tests, then — for anything with real
  correctness stakes — **revert just the fix and confirm the test actually fails**, then restore
  it. This caught two design dead-ends before they were committed (see Item 5 and Item 8 below).
- Propose a commit message prefixed `[AI] fix:`/`[AI] refactor:` and **wait for the user's
  explicit "commit"** before running `git commit` — never commit unprompted.
- A durable constraint for this whole effort: avoid the term this whole effort would otherwise be
  called, anywhere in code, comments, commit messages, or docs (this file included, hence the
  "feedback document" framing throughout).
- If a fix seems to genuinely contradict an existing spec/constitution decision, say so
  explicitly rather than silently overriding it — but proceed if the user has already given
  explicit direction (as happened for Item 1).

**Test commands**: `cd backend && mvn verify` (unit + integration; plain `mvn test` skips the
integration test — see README) · `cd frontend && npx ng test --watch=false`. As of Item 6, `mvn
verify` requires Docker running (Testcontainers spins up a real, ephemeral PostgreSQL per test
class — see Item 6's writeup).
**Current state**: 46/46 backend tests, 20/20 frontend tests, both green (Items 10/11 didn't touch
either suite — infra/docs/generated-artifact only). Items 6, 9, 11, and 13 are committed
(`03e904a`, `054bfec`, `7ddc80a`, `dc47721`); Item 10 is done but **staged, not committed**.

---

## Status: 13 of 13 items done

| # | Item | Status | Commit |
|---|---|---|---|
| 1 | Scheduler not multi-instance-safe → add ShedLock | ✅ Done | `8880aff` |
| 2 | `mostRecentCommonDate` picks a date only one currency has, causing false 404s | ✅ Done | `e3b75d9` |
| 3 | Fat `ExchangeRateController` + `TrendInsightService` duplicating series logic | ✅ Done | `8e650af` |
| 4 | `@Transactional` wraps the outbound Fixer.io HTTP call | ✅ Done | `e78bea9` |
| 5 | Cross-currency usage-counter atomicity on partial pair-lookup failure | ✅ Done | `79c6381` |
| 6 | H2 `MERGE` not Postgres-portable; README implies production/multi-instance use | ✅ Done | `03e904a` |
| 7 | `/history` returns only the derived pair rate, not each currency's raw rate (FR-014) | ✅ Done | `7addfa4` |
| 8 | `CurrencySpread`'s Appendix B table hardcoded, not externally configurable | ✅ Done | `489d707` |
| 9 | Too much dev-process narrative in code comments; move decision history to ADRs | ✅ Done | `054bfec` |
| 10 | *(optional)* docker-compose for one-command startup | ✅ Done | *(staged)* |
| 11 | *(optional)* commit a generated OpenAPI spec to the repo | ✅ Done | `7ddc80a` |
| 12 | `toUpperCase()` should use `Locale.ROOT` | ✅ Done | `610c5e4` |
| 13 | `CLAUDE.md` reads as a stale narrative snapshot, not durable working rules | ✅ Done | `dc47721` |

All 13 items from the original feedback document are now done. Item 10 (optional, confirmed in
scope) is the only one still staged rather than committed.

(Items 10/11 are explicitly optional per the feedback document itself — the user confirmed doing
both when asked, so they're "not started," not "declined.")

---

## What was done, and why it wasn't always the obvious fix

### 1. ShedLock (multi-instance scheduler lock) — `8880aff`
Added `net.javacrumbs.shedlock` (7.9.0, confirmed Spring Boot 4/Spring Framework 7 compatible),
`@EnableSchedulerLock` + a `JdbcTemplateLockProvider` bean in `SchedulingConfig`, `@SchedulerLock`
on `RateIngestionScheduler#ingestDailyRates`, and `schema.sql` for the lock table (needed because
the file-based H2 URL isn't auto-detected as "embedded," so `spring.sql.init.mode: always` had to
be set explicitly). **Note for the record**: this reverses a deliberate decision in `plan.md`'s
Complexity Tracking table ("no distributed lock library, for simplicity") — done anyway because
the user explicitly asked for it as the first item, with the tension flagged, not silently
overridden.
New test: `RateIngestionSchedulerLockTest` — proves actual mutual exclusion (two concurrent calls
contend for one DB-backed lock row), verified by temporarily removing `@SchedulerLock` and
confirming the test then fails.

### 2. `mostRecentCommonDate` bug — `e3b75d9`
Old logic: `min(currencyA's own latest date, currencyB's own latest date)` — wrong whenever the
two currencies' ingestion histories diverge (a gap in one doesn't show up in the other's "latest"
value). Replaced with `ExchangeRateRepository#findMostRecentCommonDate`, a single JPQL query
(`MAX(date) WHERE EXISTS a matching row for the other currency`) — plain JPQL, no native SQL, on
purpose, since Item 6 will need portable SQL anyway.
New tests at both the repository level (`ExchangeRateRepositoryTest`) and HTTP level
(`ExchangeRateControllerIT`) reproduce the exact divergent-history scenario. Verified by
reverting the controller-side change and confirming the HTTP test then returns `404` instead of
`200`.

### 3. Fat controller + duplication — `8e650af`
**A real design fork was surfaced here, not resolved silently**: the feedback's own diagram
proposed 4 separate controller classes; the user chose "extract services only" instead — keep
the existing 3 controller files, move all business logic into new services
(`ExchangeRateQueryService`, `HistoricalRateService`, `AnalyticsService`). `HistoricalRateService`
is the actual duplication fix: it's now the *one* place that builds the spread-adjusted
`(date, rate)` series, used by both the history endpoint and (via `InsightController`, which now
builds the series once and passes it in) `TrendInsightService`, which previously had its own
independent copy of the same logic.
Verified purely by regression: `ExchangeRateControllerIT` needed zero changes and still passed —
confirming the HTTP contract genuinely didn't move even though the internals were rebuilt.

### 4. `@Transactional` around the Fixer.io call — `e78bea9`
The feedback document's suggested fix (split into `ingestLatestRates()` calling a same-class
`@Transactional persistRates()`) would have silently done nothing — Spring's AOP proxy doesn't
intercept self-invocation, so `persistRates()` would run with no transaction at all. Used the
same `TransactionTemplate` pattern `UsageTrackingService` already established instead. New test
(`RateIngestionServiceTransactionBoundaryTest`) asserts
`TransactionSynchronizationManager.isActualTransactionActive()` is `false` from inside the mocked
Fixer.io call itself — the existing test class couldn't have caught this (its own class-level
`@Transactional` keeps a transaction active for the whole test method regardless of the fix).
Verified by reverting to the old whole-method `@Transactional` and confirming the new test failed.

### 5. Cross-currency usage-counter atomicity — `79c6381` (the riskiest one)
**A real dead end was hit and abandoned here, not papered over.** First attempt: one shared
transaction spanning both currencies, with each retry attempt as a `PROPAGATION_NESTED` savepoint.
Built it, ran the existing 50-concurrent-thread test *before* anything else — failed with
`NestedTransactionNotSupportedException` (needed a `HibernateTransactionManager`, a Spring
Framework 7 relocation). Fixed that, ran again — failed with `UnexpectedRollbackException`:
Hibernate's own `Session`/`Transaction` doesn't support nested transactions at all, confirmed
against this exact stack, regardless of the JDBC-level savepoint working correctly underneath.
**Abandoned the nested-transaction approach entirely.** Final fix: explicit compensation —
`recordLookup` tracks which currencies it already recorded; if a later currency fails after
exhausting retries, it calls new `decrementUsage`/`deleteIfZeroCount` repository methods to
explicitly undo the earlier currency's increment. The original, hard-won `REQUIRES_NEW`-per-attempt
retry mechanism (from the original T024 concurrency bug fix) is untouched.
New tests: `CurrencyUsageRepositoryTest` (the two new queries against a real DB),
`UsageTrackingServiceCompensationTest` (forces the second currency to exhaust retries, verifies
the first currency's compensation methods fire and the second's don't). Verified by reverting
just the compensation call and confirming the test failed with "wanted but not invoked."

### 7. Raw per-currency rates in `/history` — `7addfa4`
Checked the feedback document's claim against the actual spec first: FR-014 literally says "the raw rates
that are actually stored," and `spec.md`'s Key Entities define the stored entity as per-currency
— so this was a real gap, not a style preference. `HistoricalRatePoint` gained `fromRateToUsd`/
`toRateToUsd` (`null` for a same-currency pair, preserving the existing "no DB lookup at all for
same-currency" invariant). Frontend: `RateTableComponent` now shows 3 rate columns instead of 1;
verified live in the browser against the real backend for both the normal and same-currency
cases. Added `rate-table.component.spec.ts` (this component had zero test coverage before).

### 8. Externalized `CurrencySpread` — `489d707`
Moved Appendix B into `application.yml`'s `currency.spread.*`, bound via a new
`@ConfigurationProperties` record. **A claim in my own draft comment was checked and found
overstated, then corrected**: I'd written that YAML values must be quoted as strings or risk
being routed through a `double` before becoming `BigDecimal` (a constitution Principle I
concern). Tested it directly — temporarily unquoted a value, added a debug print of the exact
`BigDecimal` produced — it bound with identical precision either way in this Spring Boot version.
Kept the quoting anyway (harmless, removes a dependency on that implementation detail), but
rewrote the comments to say what was actually verified instead of overclaiming a bug that doesn't
reproduce. New tests: `CurrencySpreadPropertiesBindingTest` (real YAML → real precision, not a
fixture's assumption), `CurrencySpreadTestFixtures` (shared fixture for two pre-existing tests
that needed updating for the new constructor).

### 12. `Locale.ROOT` — `610c5e4`
The feedback document's note cited one line; grepped and found 8 unsafe `toUpperCase()` calls across 4
files, fixed all of them. Proved this matters, not just theoretically: `CurrencyCode`'s supported
set includes `IDR`, and under a Turkish default JVM locale `"idr".toUpperCase()` produces `"İDR"`
(dotted capital İ, U+0130), never matching the stored `"IDR"`. New test
(`CurrencyCodeTest`) forces the JVM default locale to Turkish and asserts the lookup still
works — reverted the fix once to confirm the test actually fails without it.

### 6. H2 → PostgreSQL + Flyway migration — *(staged, awaiting commit)*
**The biggest item, and it earned that reputation** — broken into 10 sub-steps (6.1–6.10, full
breakdown in the plan this session worked from), several of which surfaced real problems rather
than confirming the obvious approach:
- **6.1–6.2 (Flyway + migrations)**: `spring-boot-starter-flyway` + hand-written
  `V1__create_shedlock_table.sql`/`V2__create_exchange_rate_table.sql`/
  `V3__create_currency_usage_table.sql` (`backend/src/main/resources/db/migration/`), replacing
  `schema.sql` entirely. **Merely adding Flyway to the classpath, before writing any migration,
  broke 3 bare `@DataJpaTest` classes** — Spring Boot stops auto-defaulting `ddl-auto` to
  `create-drop` for an embedded DB once Flyway is present, since schema ownership is assumed to
  move to it. Fixed by adding the actual migrations, and switched every test's `ddl-auto` to
  `validate` (Flyway creates the schema; Hibernate now only cross-checks its entity mappings
  against it) instead of leaving Hibernate free to paper over a real mismatch with `create-drop`.
- **The `ddl-auto=validate` check was itself verified, not trusted blindly**: deliberately
  shrank `exchange_rate.rate_to_usd` from `DECIMAL(19,10)` to `DECIMAL(10,2)` in the migration —
  `validate` passed anyway, because Hibernate doesn't structurally check precision/scale for a
  `columnDefinition`-typed column, and no existing test exercised more than 2 decimal digits
  either. This is a real, non-obvious gap given constitution Principle I (BigDecimal precision).
  Fixed by adding `rateToUsdRoundTripsAtFullDecimalPrecision`
  (`ExchangeRateRepositoryTest`) — round-trips a 10-decimal-digit value — confirmed it actually
  fails against the shrunk column before restoring `DECIMAL(19,10)`.
- **6.3–6.4 (datasource switch + upsert rewrite)**: `ExchangeRateRepository#upsert` rewritten from
  H2's `MERGE INTO ... KEY (...)` to Postgres's `INSERT ... ON CONFLICT (currency_code, rate_date)
  DO UPDATE SET rate_to_usd = EXCLUDED.rate_to_usd, updated_at = CURRENT_TIMESTAMP` — `created_at`
  is deliberately absent from the `DO UPDATE SET` list so it survives untouched on conflict.
  `CurrencyUsageRepository`'s 4 native queries needed no rewrite (already portable ANSI SQL, per
  Item 5) and were deliberately left as-is rather than "improved" into a single `ON CONFLICT DO
  UPDATE SET query_count = query_count + 1`, which would reintroduce the exact race Item 5 proved
  unsafe. New test: `upsertInsertsFreshRowThenUpdatesRateWhilePreservingCreatedAt` (no prior test
  exercised the upsert directly at all).
- **Two more real, stack-specific traps hit and fixed while getting a boot check green against a
  real `postgres:16` container**: `spring-boot-starter-flyway` alone pulls in `flyway-core` but
  *not* a database-specific plugin — Flyway 10+ moved per-database support into separate modules,
  and without `org.flywaydb:flyway-database-postgresql` explicitly added, startup failed with
  `Unsupported Database: PostgreSQL 16.15` (confirmed this wasn't a "Postgres too new" problem —
  it failed identically against a plain, current postgres:16). Then Testcontainers 2.x (the line
  Spring Boot 4.1.0's BOM pins) turned out to have renamed its modules with a `testcontainers-`
  prefix (`testcontainers-junit-jupiter`/`testcontainers-postgresql`, not the 1.x bare
  `junit-jupiter`/`postgresql`) — confirmed by inspecting the actual BOM contents, not guessed.
- **6.7–6.9 (Testcontainers)**: new `PostgresTestContainerConfig`
  (`backend/src/test/java/.../support/`), a `@TestConfiguration` with a `@ServiceConnection
  PostgreSQLContainer("postgres:16")` bean — every one of the 10 DB-touching test classes now
  imports it (the 3 `@DataJpaTest` ones also needed `@AutoConfigureTestDatabase(replace =
  Replace.NONE)` so `@DataJpaTest` doesn't substitute its own embedded DB over the container).
  **A third real, non-obvious semantic gap surfaced here**: `ExchangeRateRepositoryTest`'s
  duplicate-constraint test caught a `DataIntegrityViolationException` and then kept issuing
  queries in the same transaction — works on H2, but PostgreSQL aborts the *entire* surrounding
  transaction on any statement error (`current transaction is aborted, commands ignored until end
  of transaction block`), not just the failing statement. Fixed with an explicit JDBC `SAVEPOINT`
  taken just before the doomed insert, rolled back to right after — scopes the abort to that one
  statement, matching what a real caller (its own fresh transaction) would actually observe.
- **The concurrency gate itself** (`UsageTrackingServiceTest`'s 50-concurrent-thread test,
  Item 5's mechanism) was re-run against real Testcontainers Postgres 4 times, not once, given how
  much this item's plan emphasized not assuming it would transfer from H2 — green every time.
- **6.6/6.10 (docs)**: README's "Known Trade-offs" H2 bullet replaced with the Postgres/Docker/
  Testcontainers reality; the "no distributed lock" bullet — stale since Item 1 added ShedLock
  *after* that bullet was written — corrected in the same pass (a pre-existing doc bug, unrelated
  to this migration, just caught while in the same section). `quickstart.md` updated to match.
Local verification used a real `postgres:16` Docker container (Docker Desktop wasn't running at
the start of this item and had to be launched) rather than a `docker-compose.yml` — Item 10 stays
separable, per the plan's own judgment call.

### 9. Comment cleanup + ADR extraction — `054bfec`
New `docs/architecture-decisions.md` with two records: **ADR-0001** (cross-currency usage-counter
concurrency design — the two failed MERGE attempts, the measured failure counts, the rejected
nested-transaction approach, and the final UPDATE-only + compensation design) and **ADR-0002**
(the ShedLock addition, reversing `plan.md`'s original no-distributed-lock decision). This is
where `UsageTrackingService`'s and `CurrencyUsageRepository#incrementUsage`'s multi-paragraph
"tried X, failed because Y, measured Z" narratives actually came from — both trimmed down to a
short pointer to the ADR plus the invariant/trade-off explanation a maintainer still needs (why
UPDATE-only, why `TransactionTemplate` not a same-class `@Transactional`, why compensation instead
of a spanning transaction). The two narrative pom.xml comments Item 6 itself flagged (the Flyway
Postgres-plugin note, the Testcontainers module-renaming note) were trimmed the same way, in
place — kept as short factual constraints, dropped the self-referential "confirmed empirically"/
"confirmed by inspecting it directly" process narration.
Separately, a mechanical sweep removed bare task-ID citations (`T007`–`T058`) from Javadoc/comments
across both stacks — 20 backend files, 7 frontend files — since Item 9's own description names
"task IDs" as one of the three things to trim, not just the two files with genuine war stories.
One incidental fix caught along the way: `FixerClientException`'s comment still said
`UpstreamFetchException` was "not yet implemented" — it has been since the original build; fixed
while removing that comment's task-ID citations, not left to bit-rot further.
Full `mvn verify` (46/46) and `ng test` (20/20) re-run after the sweep — comment-only changes, but
worth confirming nothing was accidentally altered across ~27 touched files.

### 13. `CLAUDE.md` rewrite — `dc47721`
Replaced the "Current repository state" section (a dated snapshot claiming "T056–T058 remain" and
describing only the original build — itself already stale, since it said nothing about this whole
post-submission effort or the Postgres migration) with a "Finding out what's actually true right
now" section: durable rules (check `git log`/`git branch` first, treat source/tests as the ground
truth over any doc, don't assume `[X]` means still-accurate) instead of a fact-claim that decays
the moment more work lands. Added table rows for `adjustments-log.md` and the new
`docs/architecture-decisions.md`. "Working conventions" reworded the same way — pointed at
`adjustments-log.md`'s own status table for anything past the original build, rather than
hardcoding "remaining work is T054–T058" as this file did before. Left "Implementation order"
(the five original `tasks.md` stages) and the constitution cheat-sheet alone — genuinely durable,
historical-structure information that doesn't make a current-state claim liable to decay.
Docs-only; no test re-run needed.

### 11. Committed OpenAPI spec — `7ddc80a`
Chose the lower-effort of the two options the item itself named: fetched the live
`/v3/api-docs` JSON from a real running instance and committed the pretty-printed snapshot at
`docs/openapi.json`, rather than wiring a Maven plugin to regenerate it on every build. The
build-time-generation alternative was considered and rejected: `springdoc-openapi-maven-plugin`
needs to actually boot the Spring context to introspect it, which would make every `mvn package`
depend on a live Postgres connection — a real cost for a "nice to have" artifact, and in tension
with the Simplicity principle. README now points at the file and is explicit that it's a
point-in-time snapshot, not auto-synced — the live `/v3/api-docs`/Swagger UI is the actual source
of truth if the two drift apart.
Hit a real, non-obvious environment issue while fetching it: this machine already runs a local
Homebrew PostgreSQL bound to `127.0.0.1:5432`, which silently wins over Docker's port-forwarding
proxy for any app connecting to `localhost:5432` — `docker run -p 5432:5432 postgres:16` succeeds
with no error, but the app then hits the wrong database entirely (confirmed via `lsof -iTCP:5432`
showing both bound). Same workaround as item 6's manual verification: map the container to
5433 instead of touching the user's own, unrelated Postgres service.

### 10. docker-compose for one-command startup — *(staged, awaiting commit; last item)*
Multi-stage `backend/Dockerfile` (`maven:3.9-eclipse-temurin-25` build stage with a
dependency-layer-cache step, `eclipse-temurin:25-jre-alpine` runtime — tests skipped at image-build
time, same reasoning as every other item: they need Testcontainers/a Docker socket the build
sandbox doesn't have, and already run separately via `mvn verify`) and `frontend/Dockerfile`
(`node:24-alpine` build stage, `nginx:alpine` runtime with an SPA-fallback `nginx.conf` — no Node
needed at runtime, confirmed no `@angular/ssr`/`server.ts` exist anywhere). Root
`docker-compose.yml` wires `postgres` + `backend` + `frontend`, plus a root `.env.example`.
Followed the two decisions the user made before this item's plan was finalized: nginx (not a Node
server) for the frontend, and Ollama stays a separate host prerequisite rather than a compose
service.
Two real, non-obvious things surfaced and resolved during design/verification, not assumed away:
- **The frontend's backend API URL is baked in at build time** (`environment.ts`, no runtime
  config mechanism exists). Reasoned through rather than worked around: since this is a
  client-side SPA, the *browser* makes the API call, not any container — so as long as the
  backend's port 8080 is published to the host (same as the existing manual setup), the existing
  baked-in `http://localhost:8080/api` keeps working completely unchanged. No build-arg, no
  `envsubst` trick, no source change needed.
- **Postgres's container port is deliberately NOT published to the host** in compose (unlike the
  README's standalone `docker run` command) — the backend reaches it over the compose-internal
  network by service name instead. This sidesteps, for good, the exact conflict items 6/11 hit
  personally on this machine (a local Homebrew Postgres bound to `127.0.0.1:5432` silently winning
  over Docker's port-forwarding for anything on `localhost:5432`).
- A `pg_isready` `healthcheck` + `depends_on: condition: service_healthy` on the backend avoids a
  race this session already hit manually once (a container reporting "started" well before Postgres
  is actually ready to accept connections).
- Reaching the host's own natively-run Ollama from inside the backend container needs
  `host.docker.internal` (not `localhost`, which means the container itself) — wired via
  `OLLAMA_BASE_URL` plus an `extra_hosts: host.docker.internal:host-gateway` entry, the latter
  needed for Linux Docker Engine compatibility (Docker Desktop provides it automatically).
Verified for real, not just "containers report healthy": ran `docker compose up --build` end to
end, confirmed via logs that Flyway migrated against the `postgres` *service* (not a stray local
instance), then drove the actual Calculator view in a real browser at `localhost:4200` — a genuine
cross-origin fetch to `localhost:8080` succeeded (CORS working), returned dev-seeded data, and
incremented the usage counter — and separately confirmed nginx's SPA fallback with a direct
(non-client-routed) `GET /analytics` returning `200` with `index.html`, not a 404.
Incidental cleanup: deleted a stale, gitignored `backend/data/exchangedb.mv.db` left over from
before the Postgres migration — dead weight now that H2 is gone entirely from the stack.
README updated with a new "one-command startup" section (kept as an alternative alongside the
existing manual steps, not a replacement, per the item's own framing) and a repository-layout
tree entry; root `.gitignore` gained a `.env` entry so a real `FIXER_API_KEY` never risks getting
committed.

---

## Notes for a continuation session

All 13 items are done. Item 10 (this session's last piece of work) is staged, not committed —
the user has been committing manually after each item throughout this effort, and this session
never ran `git commit` itself, only proposed messages; that's the only thing left for a
continuation session to do here, not further implementation work from this feedback document.

- **Docker is now a real prerequisite** for this project generally, not just for Item 10's
  compose file: local dev needs a Postgres instance (`docker run postgres:16`, or
  `docker compose up`), and the backend test suite needs Docker running for Testcontainers —
  `mvn verify` fails outright, not gracefully, without it.
- New durable references worth knowing about for anything *after* this list: `docs/
  architecture-decisions.md` (2 ADRs) is where decision history now lives instead of narrated
  code comments (Item 9); `docs/openapi.json` (Item 11) is a point-in-time API snapshot, not
  auto-synced; `CLAUDE.md` (Item 13) now points at both, and at this file's own status table, as
  the way to find out what's actually true rather than trusting a hardcoded snapshot anywhere.
- If new post-submission work comes in later, it almost certainly deserves its own section here
  (or a fresh log, if this one is judged long enough to retire) rather than silently reopening a
  "done" item above.
