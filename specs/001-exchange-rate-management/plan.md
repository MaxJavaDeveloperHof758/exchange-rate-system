# Implementation Plan: Exchange Rate Management System

**Branch**: `001-exchange-rate-management` | **Date**: 2026-08-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-exchange-rate-management/spec.md`

## Summary

Build a single Spring Boot backend that ingests daily FX rates from Fixer.io, serves a
spread-adjusted rate calculator, tracks per-currency usage counts, and exposes an AI-generated
trend insight via Spring AI + a local Ollama model — paired with an Angular frontend providing the
three required views (Calculator, Historical Rates & Trend Chart, Usage Analytics Dashboard).
Technical approach: a conventional layered backend (controller → service → repository) with all
monetary/rate arithmetic in `BigDecimal` (constitution Principle I/II); an idempotent,
multi-instance-safe daily scheduler backed by a DB unique constraint and native upsert
(research.md Decision 3); atomic SQL-level usage-counter increments (research.md Decision 5); and
a frontend that talks to the backend purely over a configurable HTTP base URL, with no
server-rendering or charting-library dependency (research.md Decisions 1 and 4).

## Architecture

### Components and Layers

**Backend** — a single Spring Boot application, organized by feature package
(`config`, `currency`, `rate`, `usage`, `insight`, `web`, `error`), each internally
layered controller → service → repository per constitution Principle V:

- **`web` (controllers)** — HTTP boundary only: parameter parsing/validation delegation,
  status-code mapping, DTO (de)serialization. No business logic.
- **`rate` (service + repository)** — owns the `Exchange Rate` entity, the spread-adjusted
  calculation (Principle II), the Fixer.io client, and the scheduled/manual ingestion logic.
- **`usage` (service + repository)** — owns the `Currency Usage Counter` entity and its atomic
  increment operation (Principle IV), called only from the `rate` service on a successful
  `/api/exchange` lookup.
- **`currency`** — the fixed Appendix B spread lookup and ISO currency-code validation, used by
  both `rate` and any future consumer without duplicating the table.
- **`insight`** — owns the Spring AI `ChatClient` integration: builds the grounded prompt from a
  `rate`-service history query, applies the constrained system prompt, and maps failures to a
  distinct unavailable state (Principle X).
- **`error`** — a single cross-cutting exception-to-HTTP-status mapping layer (Principle VI),
  consumed by all controllers so status-code behavior is defined in exactly one place.
- **`config`** — wiring only: the Fixer.io HTTP client bean, `@EnableScheduling`, OpenAPI/Swagger
  metadata.

**Frontend** — a single Angular SPA with a `core` layer (typed models + HTTP services, one per
backend endpoint group, mirroring [contracts/](contracts/)) and a `features` layer (one folder per
required view: `calculator`, `historical-trend`, `analytics-dashboard`), consuming `core` services
only — no feature component talks to `HttpClient` directly (Principle IX).

### End-to-End Flow per User Story

- **User Story 1 (Calculator, P1)**: `CalculatorComponent` (frontend) → `ExchangeRateService`
  (frontend `core`) → `GET /api/exchange` → `ExchangeRateController` (backend `web`) → validates
  currency codes (`currency`) → `RateCalculationService` reads the two most-relevant `Exchange
  Rate` rows (`rate` repository) and the two spreads (`currency` lookup), computes the
  spread-adjusted rate (Principle II) → on success, `UsageTrackingService` atomically increments
  both currencies (`usage` repository, Principle IV) → response DTO assembled and returned.
- **User Story 2 (Historical Trend + AI Insight, P2)**: two independent frontend calls fired in
  parallel — `HistoryService` → `GET /api/exchange/history` (returns raw points +
  `missingDates` for the table/chart) and `InsightService` → `GET /api/exchange/insight`
  (`insight` service re-reads the same range, injects it into the Spring AI `ChatClient` call
  against the local Ollama model, and returns the grounded commentary or a distinct unavailable
  state). Independently, the `rate` scheduler component fires daily against Fixer.io to keep the
  underlying data current, upserting via the native-upsert path (research.md Decision 3) so this
  story's data source is populated without any request-time dependency on the external provider
  (NFR-006).
- **User Story 3 (Usage Analytics, P3)**: `AnalyticsDashboardComponent` →
  `AnalyticsService` (frontend) → `GET /api/analytics` → `AnalyticsController` → `usage`
  repository reads all `Currency Usage Counter` rows sorted by count descending — a pure read
  path with no write side effects, entirely dependent on User Story 1 having produced usage data.

## Technical Context

**Language/Version**: Java 17+ (backend, Spring Boot) · TypeScript (frontend, Angular v15+) — the
brief's fixed floor versions per constitution's Technology Stack section; the exact pinned
versions in this repository's `pom.xml`/`frontend/package.json` may be newer and are an
implementation detail, not a planning constraint.

**Primary Dependencies**:
- Backend: Spring Web, Spring Data JPA/Hibernate, an HTTP client for the outbound Fixer.io call
  (e.g. `WebClient`, used only for that one outbound integration — the app is not reactive
  end-to-end), springdoc-openapi (Swagger UI), Spring AI's Ollama chat client.
- Frontend: Angular standalone components, `HttpClient`, Angular Reactive Forms, a small
  hand-rolled inline-SVG line-chart component (research.md Decision 1) — no charting-library
  dependency.

**Storage**: Any relational database per the brief's fixed choice (constitution Technology
Stack); local/dev/test uses an embedded or file-based engine so data survives across restarts
without extra setup, with a production-capable driver available on the classpath but not required
to run this assessment locally.

**Testing**: JUnit 5 + Mockito (backend unit + Spring Boot integration test against a real
database instance, constitution Principle VII); Angular's configured component/unit test runner
for frontend component and service tests, per brief Section 10 ("AI-generated test suites are
expected").

**Target Platform**: Local developer machine, runnable via `mvn spring-boot:run` + `ng serve`; no
containerization required for submission (brief Section 9 asks for a repository + recording, not
a deployed environment).

**Project Type**: Web application — separate backend (Spring Boot, repository root) and frontend
(Angular) with no shared build tooling between them.

**Performance Goals**: Not a load-bearing concern for this assessment beyond NFR-002/SC-003 (usage
counters exactly correct under at least 50 concurrent lookup requests) and NFR-009
(near-instant response for locally-served rate lookups, since no request-time external call is
permitted per NFR-006).

**Constraints**: Must run fully offline from the reviewer's perspective after the one-time local
model pull (NFR-005/NFR-007) — the AI insight feature must degrade gracefully, not crash the app,
if the local model isn't running. Rate lookups must never call Fixer.io synchronously (NFR-006).

**Scale/Scope**: Single-instance local demo for evaluation purposes; multi-instance correctness
(FR-004/NFR-003) is a design property proven via the DB-constraint mechanism (research.md
Decision 3), not something requiring an actual multi-instance deployment to validate for
submission.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design below.*

| # | Principle | Plan compliance |
|---|-----------|------------------|
| I | BigDecimal-only for money | `Exchange Rate.rateToUsd` and every calculation intermediate are `BigDecimal` (data-model.md); currency spreads stored/looked-up as `BigDecimal`; division uses explicit scale + `RoundingMode` at each step. |
| II | Formula fidelity | The spread service implements exactly `(toRateToUsd / fromRateToUsd) × ((100 − MAX(toSpread, fromSpread)) / 100)`; the EUR/PLN worked example (→ 4.44) is a required pinned unit test (spec.md SC-001). |
| III | Idempotent, instance-safe ingestion | DB unique constraint on `(currency_code, rate_date)` + native upsert — research.md Decision 3; rate date taken from the Fixer.io response body, never the system clock (data-model.md). |
| IV | Concurrency-safe counters | Single atomic upsert-and-increment SQL statement per currency per successful lookup — research.md Decision 5; no read-modify-write in application code. |
| V | Layered architecture | controller → service → repository per feature package; DTOs (contracts/*.md shapes) kept distinct from JPA entities (data-model.md) at the API boundary. |
| VI | API-first, correct HTTP semantics | springdoc-openapi auto-documents every controller; missing-rate lookups return `404`, invalid input returns `400`, upstream/model failures return `502`/`503` — see contracts/*.md for the full status-code matrix per endpoint. |
| VII | Tests are a deliverable | Unit coverage for every Appendix B spread tier + the worked example + edge cases (same-currency, missing data, spread ties); at least one integration test against a real database for `/api/exchange`. |
| VIII | Simplicity over speculative architecture | No message queue, no distributed-lock service, no microservices, no charting-library dependency (research.md Decisions 1, 3, 4); SSR scaffolding removed as unused surface (Decision 4). |
| IX | Frontend configurability & type safety | `environment.ts`/`environment.development.ts` expose the backend base URL; every HTTP response has a typed TS interface; every form shows loading/error/validation states distinctly. |
| X | Grounded AI insight | The exact historical series for the requested range is serialized into the prompt (contracts/insight.md); the system prompt caps output length and forbids generic filler; the insight panel has its own independent loading/error state. |

No violations requiring the Complexity Tracking table beyond the one explicitly-scoped item
below.

## Project Structure

### Documentation (this feature)

```text
specs/001-exchange-rate-management/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output — exchange.md, analytics.md, insight.md
└── tasks.md             # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
pom.xml
src/main/java/com/exchange/exchangeratesystem/
├── ExchangeRateSystemApplication.java
├── config/            # OpenAPI metadata, outbound HTTP client bean, @EnableScheduling
├── currency/           # Appendix B spread lookup, ISO currency-code validation
├── error/              # Cross-cutting exception → HTTP status mapping
├── rate/               # Exchange Rate entity/repository, Fixer.io client, ingestion
│   └── dto/            # ExchangeRateResponse, HistoricalRatePoint, etc.
├── usage/              # Currency Usage Counter entity/repository, atomic increment service
│   └── dto/            # AnalyticsResponse
├── insight/            # Spring AI ChatClient integration, prompt construction
│   └── dto/            # InsightResponse
└── web/                # ExchangeRateController, AnalyticsController, InsightController

src/test/java/com/exchange/exchangeratesystem/
├── rate/                # Spread-calculation unit tests, ingestion idempotency tests
├── usage/               # Concurrent-increment safety tests
└── web/                 # Integration test(s) against a real database

frontend/
├── src/app/
│   ├── app.routes.ts / app.config.ts   # /calculator, /trend, /analytics routes
│   ├── core/
│   │   ├── models/      # Typed response interfaces mirroring contracts/*.md
│   │   └── services/    # One HTTP service per endpoint group
│   └── features/
│       ├── calculator/
│       ├── historical-trend/           # table + inline-SVG chart + insight panel
│       └── analytics-dashboard/
└── src/environments/    # environment.ts / environment.development.ts — backend base URL
```

**Structure Decision**: Web-application layout — Spring Boot backend at the repository root,
Angular frontend in a sibling top-level directory. Both projects are independently runnable
(`mvn spring-boot:run`, `ng serve`) with no shared build tooling, per constitution Principle VIII.
This mirrors the structure already present in this repository.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| No dedicated Angular e2e test suite (Cypress/Playwright) | Unit/component tests plus the manual quickstart.md walkthrough already satisfy the rubric's testing line item and the constitution's Simplicity principle | A full e2e harness would cost setup time disproportionate to its rubric weight (the rubric asks for "Angular tests" generally, not e2e specifically) — a consciously scoped cut, not an omission |
| No distributed lock library (e.g., ShedLock) around the scheduled ingestion method | The DB unique constraint + native upsert (research.md Decision 3) already satisfies the hard requirement (correct, non-duplicated stored data under multi-instance execution) | A distributed lock additionally prevents redundant *upstream calls* from multiple instances, but spec.md's Assumptions explicitly treat provider-call efficiency as secondary to stored-data correctness — adding lock infrastructure for a secondary concern would violate Principle VIII |
