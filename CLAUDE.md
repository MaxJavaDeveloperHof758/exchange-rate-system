# CLAUDE.md

Guidance for Claude Code (and any other AI coding assistant) working in this repository.

## What this project is

**Exchange Rate Management System** — a Full Stack Developer technical assessment for a client.
Backend (Java/Spring Boot) ingests daily FX rates from Fixer.io, serves a spread-adjusted rate
calculator API, tracks per-currency usage analytics, and exposes an AI-generated trend insight
(Spring AI + a local LLM). An Angular frontend will consume that API through three views:
Calculator, Historical Rates & Trend Chart, Usage Analytics Dashboard.

The full requirements live in `.specify/` and `specs/001-exchange-rate-management/` (Spec Kit
documentation) — **read those before writing code**:

| Document | What it answers |
|---|---|
| [`.specify/memory/constitution.md`](.specify/memory/constitution.md) | The 10 non-negotiable engineering principles this project must follow, each mapped to a specific grading-rubric line item. Read this first. |
| [`specs/001-exchange-rate-management/spec.md`](specs/001-exchange-rate-management/spec.md) | Functional requirements (FR-001–022), non-functional requirements (NFR-001–009), 3 prioritized user stories with Given/When/Then acceptance criteria, key entities, measurable success criteria. |
| [`specs/001-exchange-rate-management/plan.md`](specs/001-exchange-rate-management/plan.md) | Architecture, technical context, constitution-compliance gate, project structure. |
| [`specs/001-exchange-rate-management/research.md`](specs/001-exchange-rate-management/research.md) | 6 key technical decisions with rationale and rejected alternatives (chart approach, upsert mechanism, local LLM choice, SSR handling, counter concurrency, manual-refresh scope). |
| [`specs/001-exchange-rate-management/data-model.md`](specs/001-exchange-rate-management/data-model.md) | Entities, fields, DB schema (types/precision/constraints/indexes). |
| [`specs/001-exchange-rate-management/contracts/`](specs/001-exchange-rate-management/contracts/) | Every API endpoint's request/response shape and HTTP status codes (`exchange.md`, `analytics.md`, `insight.md`). |
| [`specs/001-exchange-rate-management/quickstart.md`](specs/001-exchange-rate-management/quickstart.md) | Step-by-step local run/validation guide. |
| [`specs/001-exchange-rate-management/tasks.md`](specs/001-exchange-rate-management/tasks.md) | The 5-stage implementation task breakdown — the actual work order (see below). |
| [`specs/001-exchange-rate-management/checklists/requirements.md`](specs/001-exchange-rate-management/checklists/requirements.md) | Spec quality checklist (already passing). |
| [`specs/001-exchange-rate-management/implementation-log.md`](specs/001-exchange-rate-management/implementation-log.md) | Running log of what's been built, non-obvious bugs found/fixed, and design decisions confirmed with the user — read this before `tasks.md`'s checkboxes if you need *why*, not just *what*. |
| [`specs/001-exchange-rate-management/validation-checklist.md`](specs/001-exchange-rate-management/validation-checklist.md) | Manual end-to-end validation checklist (T054) with concrete commands/expected output per step. |
| [`specs/001-exchange-rate-management/adjustments-log.md`](specs/001-exchange-rate-management/adjustments-log.md) | Continuity record for post-submission adjustments work (bug fixes/refactors requested after the original build) — its own status table and "what's left" section are the live source of truth for that effort, not this file. May not exist, or may already be merged into `main`, depending on where the repo actually is — check `git log`/`git branch` rather than assuming either way. |
| [`docs/architecture-decisions.md`](docs/architecture-decisions.md) | Decision *history* extracted out of code comments (what was tried, what failed, why the final design won) — code comments should only explain a current invariant/trade-off; the journey belongs here. |

## Finding out what's actually true right now

This file, `tasks.md`'s checkboxes, and every prose "current state" claim anywhere in this repo
are snapshots that go stale the moment new work lands — treat all of them as *was true when
written*, never as *still true now*. Before assuming anything about the repo's status:

- Run `git log --oneline -20` and `git branch -a` first. They're authoritative; a checklist or a
  paragraph in a doc is not.
- Treat source code and passing tests as the ground truth for *what the system does*. If a doc
  (this file included) describes a class, path, or behavior that conflicts with the actual code,
  the code wins — that's a documentation lag, not a real conflict, and not grounds to re-implement
  something that already exists.
- The original build (`tasks.md`'s five stages, T001–T058) being marked complete does not mean
  no further work has happened since — check for a continuation effort (an adjustments log, an
  open branch, recent commits not reflected in any doc) before assuming the feature is "done" in
  an absolute sense.
- If you're mid-task and this file's guidance seems to contradict what you're actually observing
  in the repo, say so explicitly and confirm with the user rather than silently picking a side.

## Fixed technology stack (non-negotiable — see constitution's Technology Stack section)

- **Backend**: Java 17+, Spring Boot, Maven, Hibernate/Spring Data JPA, any relational DB (the
  constitution's floor requirement — check `backend/pom.xml`'s datasource driver and
  `backend/src/main/resources/db/migration/` for which one this repo actually runs against right
  now, since that's a choice this project has made concretely, not left open).
- **Frontend**: Angular v15+, TypeScript throughout.
- **AI Integration**: Spring AI (preferred) or LangChain4j, against a local open-source LLM
  (Ollama recommended) or an OpenAI-compatible endpoint.
- **API docs**: Swagger/OpenAPI (springdoc).
- **AI coding tool**: this assistant (Claude Code) must be a demonstrable, genuine part of the
  workflow — not just autocomplete. See "AI-Augmented Workflow" below.

## The 10 constitution principles (cheat sheet — full text in constitution.md)

1. **BigDecimal-only for money** — never `double`/`float` in rate/spread/calculation code.
2. **Formula fidelity** — the spread-adjusted formula and Appendix B spread table are fixed;
   the EUR/PLN worked example (→ 4.44) must be a pinned passing test.
3. **Idempotent, instance-safe ingestion** — DB unique constraint on `(currency_code, rate_date)`
   + native upsert; rate date comes from the Fixer.io response, never the system clock.
4. **Concurrency-safe usage counters** — atomic SQL increment, never read-modify-write in Java.
5. **Layered architecture** — controller → service → repository; DTOs distinct from entities.
6. **API-first, correct HTTP semantics** — `404` on missing rate data, `400` on invalid input,
   full Swagger UI coverage.
7. **Tests are a deliverable** — spread-calc unit tests for every Appendix B tier + worked
   example + edge cases; at least one real-database integration test.
8. **Simplicity over speculative architecture** — no message queues/microservices/premature
   abstraction; smallest design that satisfies the brief.
9. **Frontend configurability & type safety** — backend URL via environment config, no `any`
   types, every form shows loading/error/validation states.
10. **Grounded AI insight** — the trend-insight prompt must inject real historical data; system
    prompt constrains output; graceful degradation if the local model is down.

## Implementation order

Follow [`tasks.md`](specs/001-exchange-rate-management/tasks.md)'s five stages in order — each
has its own goal, an independent-test checkpoint, and a concrete file/class/method list:

1. **Этап 1 — Data Models & Database**: `ExchangeRate`, `CurrencyUsage` entities,
   `CurrencySpread` lookup, repositories with the native upsert query.
2. **Этап 2 — Scheduler & Fixer.io Integration**: HTTP client, `@Scheduled` job (12:05 AM GMT),
   idempotent ingestion service.
3. **Этап 3 — API Endpoints**: spread calculation, usage tracking, all controllers/DTOs from
   `contracts/`, exception handling, Swagger wiring.
4. **Этап 4 — AI Integration**: Spring AI `ChatClient` + Ollama, grounded prompt construction,
   `/api/exchange/insight`.
5. **Этап 5 — Frontend (Angular)**: app shell/routing, typed services, the three required views.

Do not skip ahead to a later stage's endpoint/component before its dependencies (listed in each
task) are actually done — each stage's "Independent Test" is how you confirm it before moving on.

## AI-Augmented Workflow (mandatory — 25% of the assessment grade, see constitution)

- This `.claude/` directory (skills under `.claude/skills/speckit-*`) and this `CLAUDE.md` are
  the committed AI tool configuration required by the brief — keep them substantive, not
  placeholders.
- Prefix commits that are primarily AI-generated/AI-assisted with `[AI]` so the contribution is
  traceable in history without reading every diff.
- When you (the AI) produce something the user disagrees with and it gets overridden/corrected,
  that instance should end up documented in the README's "AI Workflow" section — flag it in the
  conversation rather than silently redoing it.
- Passive, unreviewed acceptance of AI-generated code or tests is explicitly called out in the
  brief as scoring low ("Critical AI use," 5%) — apply judgment, don't just generate and commit.

## Working conventions

- Don't re-scaffold, re-plan, or re-implement work a doc claims is already done — but don't take
  the claim on faith either. Confirm against `tasks.md`'s checkboxes and, for anything past the
  original build, `adjustments-log.md`'s own status table, then against the actual code.
- If a task or doc references a class/method/path that conflicts with the actual code, resolve
  the conflict explicitly with the user before changing anything from it, rather than guessing
  which one is authoritative.
- `README.md` covers setup, architecture, an "AI Workflow" section, assumptions, and trade-offs
  (brief Section 9) — keep it in sync with any functional change. A stale README bullet describing
  an old design is a bug in the same category as a stale `tasks.md` checkbox, not just a
  documentation nicety — this file's own history (see "Finding out what's actually true right
  now" above) is the cautionary example of what letting that slide looks like.
- New decision history (a design tried and rejected, a non-obvious trade-off) belongs in
  `docs/architecture-decisions.md`, not narrated inline in a code comment — keep code comments to
  the invariant/trade-off a maintainer needs now, not the story of how it was reached.
