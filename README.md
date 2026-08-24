# Exchange Rate Management System

A spread-adjusted currency exchange calculator with a historical trend chart, an AI-generated
trend commentary, and a usage-analytics dashboard. Built as a Full Stack Developer technical
assessment for a client — the full requirements live in
[`specs/001-exchange-rate-management/spec.md`](specs/001-exchange-rate-management/spec.md) and
[`.specify/memory/constitution.md`](.specify/memory/constitution.md).

## Repository Layout

A monorepo — backend and frontend as independently buildable/runnable sibling folders, no shared
build tooling between them:

```text
.
├── backend/            # Spring Boot API (Java 25, Maven) — see backend/pom.xml
│                         (own Dockerfile inside)
├── frontend/           # Angular SPA (Angular 22, TypeScript) — own Dockerfile inside
├── docker-compose.yml  # One-command startup (backend + frontend + Postgres) — optional
├── docs/               # architecture-decisions.md (ADRs), openapi.json (a generated snapshot)
├── specs/              # Spec Kit feature spec, plan, tasks, contracts, quickstart, validation
│                         checklist → specs/001-exchange-rate-management/
├── .specify/           # Spec Kit configuration, templates, and this project's constitution
└── .claude/            # Claude Code / Spec Kit AI tool configuration — see "AI Workflow" below
```

## Prerequisites

- **Java 25** and **Maven** — `backend/pom.xml` pins `<java.version>25</java.version>`
  (the constitution's floor is Java 17+; this repo's actual pinned version is 25).
- **Node.js + npm**, and the Angular CLI (`frontend/package.json` pins `@angular/core ^22.1.0`).
- **Docker**, running, for two things: a local **PostgreSQL** instance to run the backend against
  (see step 1 below), and the backend's own test suite, which uses Testcontainers to spin up a
  real (ephemeral) PostgreSQL per test class rather than mocking the database.
- A **Fixer.io free-tier API key** ([fixer.io](https://fixer.io)) — read from an environment
  variable, never hardcoded (see below).
- **[Ollama](https://ollama.com)** installed locally, for the AI trend insight — or any
  OpenAI-compatible endpoint, by pointing the same two properties (below) at it instead.

## Setup & Run

### 0. One-command startup (docker compose, optional)

An alternative to the manual steps below — builds and runs Postgres, the backend, and the
frontend together:

```bash
cp .env.example .env   # then fill in FIXER_API_KEY
docker compose up --build
```

Frontend: `http://localhost:4200`. Backend: `http://localhost:8080` (Swagger UI included) — the
same ports as the manual setup below, so nothing about the "normal" URLs changes. Ollama is
deliberately **not** a compose service — it stays a separate host-level prerequisite (see below);
the backend reaches a natively-running Ollama via Docker's `host.docker.internal`, which
`docker-compose.yml` already wires up. If Ollama isn't running, the AI insight endpoint degrades
gracefully (`503`) exactly as it does outside compose too.

### 1. Backend

```bash
docker run --name exchange-postgres \
  -e POSTGRES_DB=exchangedb -e POSTGRES_USER=exchange -e POSTGRES_PASSWORD=exchange \
  -p 5432:5432 -d postgres:16

cd backend
export FIXER_API_KEY=your-fixer-io-key   # required — startup fails fast without it
export SPRING_PROFILES_ACTIVE=dev        # activates DevDataSeeder
mvn spring-boot:run
```

`application.yml`'s datasource defaults (`DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD`
environment variables, all optional) match the `docker run` command above exactly — no extra
configuration needed for a first run. Flyway (`backend/src/main/resources/db/migration/`) owns
schema creation for every profile, including `dev`; the `dev` profile itself only activates
`DevDataSeeder`, which idempotently seeds 7 days of the brief's EUR/PLN worked-example rates on
every startup — safe to restart repeatedly, never duplicates rows — so the Calculator and
Historical Trend views have real data to show without waiting on Fixer.io or the daily scheduler.

The backend starts on `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui/index.html`.
A generated snapshot of the same OpenAPI 3.0 document is committed at
[`docs/openapi.json`](docs/openapi.json) — a point-in-time export (fetched from `/v3/api-docs`
against a running instance), not something regenerated automatically on every build; the live
`/v3/api-docs`/Swagger UI is the actual source of truth if the two ever diverge.

**Local AI model (Ollama)**:

```bash
ollama pull llama3.2   # the model application.yml defaults to (spring.ai.ollama.chat.options.model)
ollama serve            # usually starts automatically on install; base URL defaults to http://localhost:11434
```

Both are overridable without touching code: `OLLAMA_BASE_URL` and `OLLAMA_MODEL` environment
variables (`backend/src/main/resources/application.yml`) — point these at any
Ollama-API-compatible endpoint (including a remote one) to skip a local model pull entirely. If
Ollama isn't reachable, `GET /api/exchange/insight` returns a `503 INSIGHT_UNAVAILABLE` — the
rest of the app, including rate history, is unaffected (see "Known Trade-offs" below).

### 2. Frontend

```bash
cd frontend
npm install
ng serve
```

Open `http://localhost:4200`. The backend base URL is read from
`frontend/src/environments/environment.development.ts` (`apiBaseUrl`) — change that one file, no
source-code change elsewhere, to point at a different backend host/port. If you do, also update
`backend/src/main/resources/application.yml`'s `frontend.origin` (or set the `FRONTEND_ORIGIN`
env var) so CORS allows the new origin.

### 3. Try it

- **Calculator** (`/calculator`): convert between two currencies, see both currencies' running
  query counts.
- **Historical Trend** (`/trend`): pick a pair and date range for a table + line chart of stored
  rates, plus an independently-loading AI commentary on the trend.
- **Analytics** (`/analytics`): a ranked usage chart across every currency ever queried.

For a full manual walkthrough with expected responses at every step — including the concurrency
and error-path checks — see
[`specs/001-exchange-rate-management/quickstart.md`](specs/001-exchange-rate-management/quickstart.md)
and [`specs/001-exchange-rate-management/validation-checklist.md`](specs/001-exchange-rate-management/validation-checklist.md).

## Running Tests

```bash
cd backend && mvn verify   # mvn test alone SKIPS the integration test — it's bound to
                            # maven-failsafe-plugin's integration-test/verify goals, not
                            # maven-surefire-plugin's test goal
```

```bash
cd frontend && npm test    # ng test, Vitest-backed per frontend/package.json
```

26 backend tests (unit + one real-database integration test) and 16 frontend component/unit
tests, all currently passing.

## Architecture Overview

**Backend** — a single Spring Boot app, organized by feature package, each internally layered
controller → service → repository (constitution Principle V):

| Package | Owns |
|---|---|
| `web` | `ExchangeRateController`, `InsightController`, `AnalyticsController` — HTTP boundary only |
| `rate` | `ExchangeRate` entity/repository, `SpreadCalculationService`, `FixerClient`, `RateIngestionService`/`Scheduler` |
| `usage` | `CurrencyUsage` entity/repository, `UsageTrackingService`'s atomic per-currency increment |
| `currency` | The fixed Appendix B spread lookup and the supported-currency-code set |
| `insight` | `TrendInsightService` — Spring AI `ChatClient` + Ollama, grounded prompt construction |
| `error` | One cross-cutting exception → HTTP-status mapping (`GlobalExceptionHandler`) |
| `config` | Fixer.io `WebClient` bean, `@EnableScheduling`, OpenAPI metadata, CORS |

Every monetary/rate value is `BigDecimal` end to end (constitution Principle I); ingestion is
idempotent via a DB unique constraint on `(currency_code, rate_date)` plus a native upsert, so a
retried or overlapping ingestion run never duplicates a day's rate; usage counters increment via a
single atomic SQL statement per currency, never a read-modify-write in application code.

**Frontend** — an Angular SPA with a `core` layer (typed response models + one `HttpClient`
service per backend endpoint group, mirroring [`contracts/`](specs/001-exchange-rate-management/contracts/))
and a `features` layer (`calculator`, `historical-trend`, `analytics-dashboard`) — no feature
component talks to `HttpClient` directly. The trend chart and the analytics ranking are both
hand-rolled inline SVG, not a charting library (see "Known Trade-offs").

**API endpoints**:

| Method & Path | Purpose |
|---|---|
| `GET /api/exchange` | Spread-adjusted rate for a currency pair (optionally on a specific date) |
| `GET /api/exchange/history` | Raw `(date, rate)` series for a pair + date range, with `missingDates` |
| `POST /api/exchange/refresh` | On-demand Fixer.io ingestion (never touches usage counters) |
| `GET /api/exchange/insight` | AI-generated commentary on a pair's trend over a date range |
| `GET /api/analytics` | Usage counts per currency, ranked descending |

Full request/response shapes and status codes: [`specs/001-exchange-rate-management/contracts/`](specs/001-exchange-rate-management/contracts/).

## AI Workflow

**Tool used**: [Claude Code](https://claude.com/claude-code), used throughout planning
*and* implementation — not autocomplete-only usage.

**Configuration**:
- [`CLAUDE.md`](CLAUDE.md) — project-specific guidance (tech stack, task order, working
  conventions) loaded into every session.
- [`.claude/skills/speckit-*`](.claude/skills/) — the GitHub Spec Kit workflow (`specify` →
  `clarify` → `plan` → `tasks` → `implement` → `analyze`), which produced
  [`spec.md`](specs/001-exchange-rate-management/spec.md),
  [`plan.md`](specs/001-exchange-rate-management/plan.md),
  [`research.md`](specs/001-exchange-rate-management/research.md), and
  [`tasks.md`](specs/001-exchange-rate-management/tasks.md) *before* any implementation commit —
  each pinned by a commit timestamp that predates the code it describes.
- [`.specify/memory/constitution.md`](.specify/memory/constitution.md) — 10 non-negotiable
  engineering principles, each checked against `plan.md` before implementation began (see that
  file's "Constitution Check" table) and re-verified at points below.
- One task from `tasks.md` per agent session, each ending in an explicit developer review before
  the next was started — every commit is prefixed `[AI]` so AI-assisted work stays traceable in
  history without reading every diff.

**Concrete instances where the agent's output was overridden or corrected**:

1. **A real concurrency bug, caught by testing rather than by review.** The first two
   implementations of the per-currency usage-counter increment (`UsageTrackingService`/T024)
   looked correct on inspection but weren't: attempt 1 (H2 `MERGE` with the increment computed via
   a scalar subquery) lost updates under load — 50 concurrent requests produced a final count of
   8, not 50; attempt 2 (ANSI `MERGE ... WHEN MATCHED/NOT MATCHED`) fixed the matched-row case but
   still raced on a brand-new currency's first-ever insert (9 threads failed with a constraint
   violation, final count 41). Only a real 50-thread concurrency test (not code review) surfaced
   either bug. The fix — a separate, fully-native `insertNewRow` path for a currency's first
   lookup, with no entity ever attached to a persistence context — was verified with 5 repeated
   reruns, all exactly 50/50, before being accepted. See
   [`specs/001-exchange-rate-management/implementation-log.md`](specs/001-exchange-rate-management/implementation-log.md#stage-3--api-endpoints-t020t028)
   for the full account.
2. **An unrequested API surface, reverted to match the spec.** An early draft of
   `UsageTrackingService` added a second public overload beyond what `tasks.md` specified. The
   developer asked for the method signature to match the task description exactly rather than
   accept the embellishment; the extra overload was removed and the retry logic that had motivated
   it was kept as a private implementation detail instead.
3. **Design choices surfaced as explicit questions, not silent defaults.** For example, when
   building the Usage Analytics Dashboard's ranked visualization (T052), the agent asked whether to
   render it with plain CSS-width bars or with SVG `<rect>`s matching the existing trend chart's
   pattern, rather than picking one unilaterally; the developer directed it to keep the existing
   inline-SVG convention for consistency. The same session also surfaced (and got an explicit
   answer on) whether the trend-chart line vs. a charting library was the right call before any
   code was written for it.

**Commit convention**: every AI-assisted commit is prefixed `[AI]`, with a scope suffix per area
(`config:`, `service:`, `controller:`, `frontend:`, `docs:`, etc.) — see `git log` for the full,
unsquashed history.

## Assumptions

(Full list with rationale: [`spec.md`'s Assumptions section](specs/001-exchange-rate-management/spec.md#assumptions).)

- Fixer.io is called at most once per day by the scheduler; rate lookups are always served from
  locally stored data, never an on-demand provider call.
- This is a trusted-network internal tool — no authentication, authorization, or per-user data
  isolation.
- Historical rate data is read-only once ingested; there's no UI to edit or delete a stored rate.
- The AI trend insight is best-effort qualitative commentary, not financial precision — its
  unavailability must degrade gracefully, never block the rest of the trend view.
- "Usage" is tracked per currency (a lookup increments both the source and target currency's
  counters), not per ordered pair.

## Known Trade-offs

- **PostgreSQL + Flyway, both local/dev/test and the intended production path** — Docker is now a
  real local-dev and test-suite prerequisite: the backend datasource is PostgreSQL only (no H2
  fallback), and the test suite uses Testcontainers (a real, ephemeral Postgres per test class)
  rather than an in-memory substitute. `ExchangeRateRepository#upsert` uses Postgres's
  `INSERT ... ON CONFLICT DO UPDATE`; schema is Flyway-migration-owned
  (`backend/src/main/resources/db/migration/`) for every profile, not Hibernate `ddl-auto`.
- **No charting library** (`SvgLineChartComponent`, the analytics bar chart) — a small hand-rolled
  SVG mapping function instead of ngx-charts/Chart.js/D3, per the brief's own "the chart does not
  need to be elaborate" and the constitution's Simplicity principle. See
  [`research.md` Decision 1](specs/001-exchange-rate-management/research.md).
- **ShedLock guards the scheduled ingestion job against double-firing across instances** — a
  JDBC-backed distributed lock (`net.javacrumbs.shedlock`) ensures only one instance's 00:05 GMT
  trigger actually calls Fixer.io and ingests, even when multiple instances are pointed at the
  same database. The DB unique constraint + upsert were already sufficient for *stored*-data
  correctness on their own (multiple concurrent ingests of the same day can't corrupt data); the
  lock's purpose is avoiding redundant Fixer.io calls, not a correctness requirement of last
  resort.
- **A curated ~42-code currency set, not the full ISO 4217 list** — sized to cover every Appendix B
  tier and the worked example, not exhaustive real-world coverage.
- **No dedicated Angular e2e suite** (Cypress/Playwright) — component/unit tests plus the manual
  `quickstart.md`/`validation-checklist.md` walkthrough were judged sufficient for this
  assessment's scope; a full e2e harness was a consciously scoped cut, not an oversight.

## Further Reading

- [`specs/001-exchange-rate-management/spec.md`](specs/001-exchange-rate-management/spec.md) —
  full functional/non-functional requirements and acceptance criteria.
- [`specs/001-exchange-rate-management/plan.md`](specs/001-exchange-rate-management/plan.md) —
  architecture rationale and the constitution-compliance gate.
- [`specs/001-exchange-rate-management/contracts/`](specs/001-exchange-rate-management/contracts/) —
  every endpoint's exact request/response shape.
- [`specs/001-exchange-rate-management/implementation-log.md`](specs/001-exchange-rate-management/implementation-log.md) —
  a running, non-obvious-bugs-and-decisions log kept throughout implementation.
- [`specs/001-exchange-rate-management/validation-checklist.md`](specs/001-exchange-rate-management/validation-checklist.md) —
  the manual end-to-end validation checklist (T054), with ready-to-run commands.
