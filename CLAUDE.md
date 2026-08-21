# CLAUDE.md

Guidance for Claude Code (and any other AI coding assistant) working in this repository.

## What this project is

**Exchange Rate Management System** — a Marcura Full Stack Developer technical assessment.
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

## Current repository state (important — read before assuming anything is built)

This repository is currently a **bare skeleton**:

- `pom.xml` is a plain Maven POM (`groupId: com.exchange`, `artifactId: exchange-rate-system`,
  Java 25 compiler settings) with **no Spring Boot parent and no dependencies declared yet**.
- `src/main/java/` and `src/test/java/` exist but contain **no packages or classes yet**.
- There is **no `frontend/` directory yet** — the Angular app has not been scaffolded here.
- There is **no `.git` repository initialized yet** in this directory.

⚠️ **Known mismatch to resolve before/while starting Stage 1**: the copied planning docs
(`plan.md`, `data-model.md`, `tasks.md`) all assume the Java package
`com.marcura.exchangeratesystem`, but this project's actual Maven coordinates are
`com.exchange` / `exchange-rate-system`. Decide (with the user) whether to rename the package
in the docs to `com.exchange.exchangeratesystem` (or similar) or update `pom.xml`'s `groupId` to
`com.marcura` for consistency — do this once, explicitly, before generating any code, rather than
letting the two drift.

Do not assume any backend logic, entities, or endpoints already exist — `tasks.md`'s five stages
are all still `[ ]` unchecked. Treat `tasks.md` as the literal, ordered to-do list.

## Fixed technology stack (non-negotiable — see constitution's Technology Stack section)

- **Backend**: Java 17+, Spring Boot, Maven, Hibernate/Spring Data JPA, any relational DB.
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

- This is documentation-only territory until the user explicitly asks for implementation — don't
  start writing backend/frontend code from a documentation request alone.
- If a task in `tasks.md` references a class/method/path that conflicts with the actual
  `pom.xml`/package structure (see the mismatch flagged above), resolve the conflict explicitly
  with the user before generating code from it, rather than guessing.
- No README.md exists yet at the repository root; the brief (Section 9) requires one covering
  setup, architecture, an "AI Workflow" section, assumptions, and trade-offs — this is separate
  from this `CLAUDE.md` and should be created when implementation is underway, not before.
