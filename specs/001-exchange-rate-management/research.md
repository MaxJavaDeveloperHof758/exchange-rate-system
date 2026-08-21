# Phase 0 Research: Exchange Rate Management System

**Input**: [spec.md](spec.md), [constitution.md](../../.specify/memory/constitution.md)

This document resolves every open technical question implied by the Technical Context in
[plan.md](plan.md) before Phase 1 design begins. Each decision states what was chosen, why, and
what alternatives were rejected.

## Decision 1 — Historical Trend Chart Implementation

**Decision**: Render the line chart with a small, hand-rolled Angular component that maps
`{date, rate}[]` points to an inline SVG `<polyline>` plus simple axis ticks — no third-party
charting library dependency.

**Rationale**: Brief Section 5.2 states explicitly, "the chart does not need to be elaborate —
clarity of the trend is what matters." A charting library (ngx-charts, Chart.js, ECharts, etc.)
adds a dependency, a wrapper-component integration surface, and bundle size for a requirement
that a ~60-line pure function already satisfies. This keeps the frontend aligned with
constitution Principle VIII (Simplicity Over Speculative Architecture) and keeps the mapping
function trivially unit-testable in isolation (input points in, SVG path out — no DOM, no
library API surface to mock).

**Alternatives considered**:
- *ngx-charts / Chart.js*: More polished visuals (tooltips, animations) out of the box, but pulls
  in a dependency and its own configuration API for a requirement the brief explicitly says does
  not need to be elaborate. Rejected as disproportionate.
- *D3.js*: Maximum flexibility, but is a full data-visualization framework — steep setup cost for
  a single line chart. Rejected as over-engineering for this scope.

## Decision 2 — Local LLM Model Choice for Spring AI / Ollama

**Decision**: Use Ollama running locally with a small, widely-available instruction-tuned model
(e.g., `llama3.2`) as the default, configured via a single Spring Boot property
(`spring.ai.ollama.chat.options.model`), with the model name externalized so a reviewer can swap
it without a code change.

**Rationale**: Brief Section 7.2 states, "Ollama is the simplest setup" and "the model choice is
yours." A small instruction-tuned model is sufficient because the task (Section 7.3: "We are not
expecting financial accuracy, a fine-tuned model, or a production-grade RAG pipeline") is a short
grounded-summary task, not complex reasoning — a smaller model also keeps the reviewer's local
setup fast (`ollama pull` completes quickly) and keeps response latency low enough for a
synchronous request/response endpoint pattern.

**Alternatives considered**:
- *A larger local model (e.g., 70B-class)*: Marginally better prose quality, but multi-minute pull
  time and much higher local hardware requirements for reviewers — directly works against brief
  Section 7.2's "reviewers can run it locally without configuration guesswork" and NFR-007 (Local
  Runnability). Rejected.
- *A hosted OpenAI-compatible endpoint*: Explicitly allowed by the brief ("any OpenAI-compatible
  endpoint"), but requires an API key a reviewer must obtain and pay for, which is a worse
  reviewer experience than a one-time local `ollama pull`. Kept as a documented fallback in
  quickstart.md, not the default.

## Decision 3 — Idempotent, Multi-Instance-Safe Ingestion Mechanism

**Decision**: Enforce a database-level unique constraint on `(currency_code, rate_date)` in the
exchange-rate table, and implement the daily upsert as a single native "insert, or update on
conflict" statement (e.g., H2/PostgreSQL `MERGE`/`ON CONFLICT DO UPDATE`) executed per currency
inside one transaction per ingestion run — not a "select, then decide insert vs. update in Java"
pattern.

**Rationale**: Per constitution Principle III and spec.md NFR-003, correctness must hold even if
the job fires concurrently from more than one running instance at the same scheduled moment. A
DB-level unique constraint plus a native upsert is atomic at the database's own concurrency-control
layer — two instances racing to upsert the same `(currency, date)` row either serialize cleanly or
one is safely rejected/merged, with no window for a duplicate row to be created between a Java-side
existence check and the subsequent write. This is simpler than introducing a distributed lock
library and requires no additional infrastructure (per Principle VIII).

**Alternatives considered**:
- *Application-level "find by currency+date, then insert or update"*: Has a classic
  check-then-act race window under concurrent execution from multiple instances — two instances
  can both find "not present" and both insert, violating FR-003/FR-004 unless the unique
  constraint is also present (in which case the constraint is doing the real work anyway, and the
  Java-side check becomes redundant, error-prone ceremony). Rejected as the sole mechanism.
- *A distributed lock (e.g., ShedLock) around the whole scheduled method*: Also a valid, brief-
  sanctioned approach ("the approach and its justification matter more than the specific
  mechanism"), and prevents redundant upstream Fixer.io calls from multiple instances, which the
  DB-constraint approach does not by itself prevent. Rejected as the primary mechanism only
  because spec.md's Assumptions section explicitly treats "stored-data correctness" as the hard
  requirement and "provider call efficiency" as secondary — the DB constraint alone already
  satisfies the hard requirement with less moving infrastructure. This alternative is noted in
  plan.md's Complexity Tracking as a documented, consciously-rejected upgrade path, not a gap.

## Decision 4 — Angular Scaffold SSR Handling

**Decision**: Strip server-side-rendering (SSR) scaffolding (`server.ts`, `main.server.ts`,
`app.config.server.ts`, `app.routes.server.ts`, the `express`/`@angular/ssr` dependencies, and the
`server`/`serve-ssr` build targets) from the Angular project, since this is an internal SPA
consumed directly via `ng serve` against a configurable backend URL.

**Rationale**: Brief Section 5 requires "a clean, navigable single-page application" run via
`ng serve` — there is no requirement for server-rendering, and carrying unused SSR
scaffolding adds build-configuration surface area and reviewer confusion with no corresponding
rubric credit. Consistent with constitution Principle VIII.

**Alternatives considered**:
- *Keep SSR scaffolding dormant, unused*: No functional harm, but adds dead configuration a
  reviewer must read past, and risks an unused build target failing during evaluation if a
  dependency is missing. Rejected in favor of a smaller, fully-used surface.

## Decision 5 — Concurrency-Safe Usage Counter Mechanism

**Decision**: A single atomic SQL statement per currency per successful lookup —
`INSERT ... ON CONFLICT (currency_code) DO UPDATE SET query_count = query_count + 1, last_queried_date = excluded.last_queried_date`
(or the equivalent H2 `MERGE` syntax) — executed directly against the database, not a
read-entity → increment-in-Java → save-entity pattern.

**Rationale**: Constitution Principle IV and spec.md NFR-002 require zero lost/duplicated
increments under at least 50 concurrent successful lookups. A read-modify-write cycle performed in
application code is vulnerable to lost updates under concurrent access unless wrapped in
pessimistic locking (which serializes throughput) or optimistic locking with retry (which adds
retry-loop complexity). A single atomic upsert-and-increment statement delegates the
concurrency guarantee to the database's own row-level atomicity, which is simpler and requires no
additional locking code.

**Alternatives considered**:
- *`@Version`-based optimistic locking with an application-level retry loop*: Correct, but adds
  retry-loop code and potential retry storms under high contention on a single hot row (the
  currency-usage row for a popular currency is exactly a hot row). Rejected as more complex than
  necessary for this scope (Principle VIII).
- *A single global lock around the increment*: Correct but serializes all usage tracking
  regardless of which currency is involved, unnecessarily limiting throughput. Rejected.

## Decision 6 — Manual Refresh Endpoint Scope (Optional, FR-022)

**Decision**: If implemented, the optional manual-refresh endpoint reuses the exact same ingestion
service/upsert path as the scheduled job (Decision 3), triggered synchronously on request, and
explicitly does not call any usage-counter code path.

**Rationale**: Brief Section 4.4 requires the manual trigger to upsert "without affecting existing
usage counters." Reusing the same ingestion code path (rather than a parallel implementation)
guarantees this by construction — the ingestion path never touches `CurrencyUsage` in the first
place, so there is no separate behavior to keep in sync or accidentally diverge.

**Alternatives considered**:
- *A separate, simplified "just fetch and save" implementation for manual refresh*: Duplicates the
  upsert logic, risking behavioral drift (e.g., a future change to the idempotency mechanism
  applied to one path and not the other). Rejected in favor of one ingestion code path with two
  triggers (scheduled, manual).
