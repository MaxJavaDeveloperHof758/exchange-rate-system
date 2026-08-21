<!--
Sync Impact Report
- Version change: 1.0.0 → 1.0.0 (full rewrite, no prior version superseded — treated as a fresh
  ratification per explicit instruction to recreate this document from scratch, source-of-truth
  re-derived directly from the assessment brief PDF rather than from the prior draft)
- Modified principles: all ten principles rewritten for direct, explicit traceability to Section 10
  (Grading Rubric) line items and weights; principle count and general subject areas unchanged
  (I–X), wording and rationale expanded and re-grounded in brief section numbers.
- Added sections:
  - Core Principles (I–X)
  - Technology Stack (Fixed, Non-Negotiable)
  - Assessment Deliverables & Submission Requirements
  - AI-Augmented Workflow Requirements
  - Governance
- Removed sections: none (prior draft's sections retained under the same names, content
  re-derived and expanded)
- Deferred / TODO items: none — ratification date confirmed as the date this rewrite was
  performed (2026-08-21); no unknown fields remain.
- Templates requiring follow-up review: plan-template.md, spec-template.md, tasks-template.md,
  checklist-template.md — no structural changes required; they read this constitution at runtime
  and were not modified by this command.
-->

# Exchange Rate Management System Constitution

## Core Principles

### I. BigDecimal-Only for Monetary and Rate Values (NON-NEGOTIABLE)

Every exchange rate, spread percentage, and calculated adjusted-rate value MUST be represented as
`java.math.BigDecimal` with an explicit scale and `RoundingMode` at every arithmetic step
(division, multiplication, percentage application). `double`/`float` MUST NOT appear anywhere in
the rate-calculation, persistence, or API-serialization path — including intermediate variables,
entity fields, and DTO fields. Every division MUST use the three-argument
`BigDecimal.divide(divisor, scale, RoundingMode)` (or the `MathContext` equivalent) — never the
zero-argument overload, which throws `ArithmeticException` on non-terminating decimals such as
`3.7 / 0.8` chains that don't terminate cleanly at arbitrary precision.

**Rationale**: The brief names "BigDecimal usage" explicitly under Code Quality (Section 10,
"Code quality" row, 6%, "What We Look For"). A financial rate calculator that silently loses
precision to floating-point representation error is a correctness defect, not a style
preference, and is trivially detectable by any reviewer inspecting the code.

### II. Formula Fidelity to the Brief (NON-NEGOTIABLE)

The spread-adjusted rate MUST be computed exactly as specified in brief Section 6.1, with no
reinterpretation:

```
adjustedRate = (toCurrencyRateToUSD / fromCurrencyRateToUSD) × ((100 − MAX(toSpread, fromSpread)) / 100)
```

The spread lookup MUST follow the fixed table in brief Appendix B, with no additional tiers
invented and no currency reassigned between tiers:
- Base currency (as returned by the Fixer.io API key): 0.00%
- JPY, HKD, KRW: 3.25%
- MYR, INR, MXN: 4.50%
- RUB, CNY, ZAR: 6.00%
- All other currencies: 2.75%

The worked example in brief Section 6.2 (EUR rate-to-USD 0.8 / spread 1%; PLN rate-to-USD 3.7 /
spread 4% → adjusted rate 4.44) MUST exist as a pinned, passing unit test before the calculation
is considered complete.

**Rationale**: This formula is the single most objectively and mechanically checkable piece of
the entire assessment (Section 10, "Core API correctness," 8%: "correct formula"). Getting it
wrong costs marks regardless of how polished the surrounding system is, and there is no
ambiguity in the brief to justify a deviation.

### III. Idempotent, Instance-Safe Daily Ingestion

The scheduled Fixer.io fetch (brief Section 4.1: once daily at 12:05 AM GMT) MUST persist the
rate date exactly as reported in the API response — never `LocalDate.now()` or any other
system-clock-derived value at fetch time. Re-running the job for a currency/date combination that
already exists MUST update the existing record in place (upsert), never producing a second row
for the same `(currency_code, rate_date)` pair; this MUST be enforced at the database level via a
unique constraint, not solely by an application-level existence check that can race. Because the
brief explicitly states the service "may run as multiple instances in production" (Section 4.1),
the scheduling/locking approach — whichever specific mechanism is chosen (e.g., a DB unique
constraint making concurrent upserts naturally idempotent, a distributed lock library such as
ShedLock, or a leader-election pattern) — MUST be explicitly documented with its justification;
the brief states "the approach and its justification matter more than the specific mechanism."

**Rationale**: Section 10, "Data persistence & scheduler" (6%) explicitly grades "upsert
correctness, date field from API response, scheduler correctness under multi-instance
deployment." An ungrounded or undocumented mechanism scores no better than a broken one under
this rubric line.

### IV. Concurrency-Safe Usage Counters

Every successful `/exchange` query MUST atomically increment a per-currency usage counter for
both the source and target currency (brief Section 4.2). The increment MUST be implemented as a
single database-level atomic operation (e.g., `UPDATE ... SET query_count = query_count + 1 ...`,
or an equivalent pessimistic/optimistic-locking strategy that is verified not to lose updates).
A read-then-compute-then-save pattern executed in application code MUST NOT be used for this
counter, since concurrent requests can interleave between the read and the write and silently
lose increments. The chosen approach and its justification MUST be documented alongside the
implementation.

**Rationale**: Section 10, "Concurrency & thread safety" (5%) states explicitly that "the
increment must be safe under concurrent requests" and that "approach and justification matter
more than the specific solution" — but an approach that is demonstrably unsafe under concurrent
load still fails this bar regardless of how it is justified.

### V. Layered Architecture, No Code Smells

Code MUST be organized in clear, conventional layers — controller → service → repository — with
request/response DTOs kept distinct from JPA entities at the API boundary; entities MUST NOT be
returned directly from controllers. Any JPA query with a non-trivial predicate MUST be expressed
as a derived query method name or an explicit `@Query` (named query), never fetched broadly and
filtered in Java. Classes and methods MUST have a single, clearly identifiable responsibility;
god-classes, god-services, and duplicated logic scattered across layers MUST be avoided.

**Rationale**: Section 10, "Code quality" (6%) grades "separation of concerns, named queries, no
obvious code smells" as an explicit, standalone line item independent of whether the feature
behaves correctly.

### VI. API-First with Correct HTTP Semantics

Every REST endpoint MUST be documented via springdoc-openapi (or equivalent) and be browsable and
exercisable in Swagger UI with no manual wiring gaps or undocumented parameters (brief Section 3,
"API Documentation: Swagger / OpenAPI"). Endpoints MUST return semantically correct HTTP status
codes: `404` when rate data for a requested date does not exist (brief Section 4.2, "return an
appropriate HTTP error"), `400` for invalid or malformed input (unrecognized currency code,
malformed date), and `200` only for a genuinely complete, successful response. Error response
bodies MUST include a machine-readable, specific reason — not a bare generic message — so a
frontend or reviewer can distinguish failure modes programmatically.

**Rationale**: Section 10 weights this across two line items: "Core API correctness" (8%: "HTTP
semantics ... 404 on missing rates") and "API documentation" (3%: "All endpoints documented,
Swagger UI functional").

### VII. Tests Are a Deliverable, Not an Afterthought

Unit tests MUST cover the spread-calculation logic across every tier of the Appendix B table
(base currency, JPY/HKD/KRW group, MYR/INR/MXN group, RUB/CNY/ZAR group, all-other-currencies
group), including the brief's pinned worked example, plus edge cases: identical currency on both
sides of a pair, missing rate data for the requested date, and boundary conditions where the two
spreads in a pair are equal. At least one integration test MUST exercise the `/exchange` endpoint
end-to-end against a real (e.g., H2, in-memory or file-based) database, not a fully mocked
repository layer. Per brief Section 8.1 ("using AI to generate your test coverage rather than
writing tests manually") and Section 10 ("AI-generated test suites are expected"), test
generation SHOULD be AI-assisted first, with human review and correction of assertions before the
suite is trusted as a completeness signal — passive, unreviewed acceptance of AI-generated tests
is itself a documented risk under Principle X below.

**Rationale**: Section 10, "Testing" (4%) explicitly names "coverage of spread calculation logic
and at least one integration test," and Section 8 makes AI-assisted test generation an explicit
expectation, not merely an allowed shortcut.

### VIII. Simplicity Over Speculative Architecture

The smallest design that correctly and completely satisfies the brief MUST be preferred over
speculative abstraction. No message queues, no microservices, no premature caching layers, and no
generic "framework-within-the-framework" abstraction layers are to be introduced unless a
specific stated requirement (e.g., multi-instance scheduler safety, Principle III) demands a
mechanism to satisfy it — in which case the minimal mechanism that satisfies that specific
requirement is used, and nothing broader. A working, complete, end-to-end system always outranks
an elaborate but partially non-functional one.

**Rationale**: The brief states directly, in Section 7.3, "A well-integrated simple solution
scores higher than an over-engineered one that does not run," and the assessment's overall
scoring model (Section 10 closing note) confirms that a complete, simple, correctly-scoped
solution is the target — over-scoping risks running out of time before every required view and
endpoint works end-to-end.

### IX. Frontend Configurability and Type Safety

The Angular application MUST run via `ng serve` against a backend base URL supplied through a
configurable environment file (e.g., `environment.ts` / `environment.development.ts`), with zero
source-code changes required for a reviewer running the project locally against their own backend
port or host (brief Section 5.4: "a configurable environment variable, so reviewers can run it
locally without code changes"). Every HTTP response consumed by a component MUST be represented
by a typed TypeScript interface or class — `any` MUST NOT be used for API response shapes. Every
user-facing form (the Calculator's currency/date inputs, the Historical view's pair/date-range
picker) MUST expose three distinguishable states to the user: a validation-error state for bad
input, a loading state while a request is in flight, and a distinct error state when the backend
call fails — a silently blank or frozen UI on any of these paths is a defect.

**Rationale**: Section 10 weights "Calculator view" (6%: "form validation, error handling,
loading states, typed models") explicitly, and Section 5.4's wording on configurability is a
literal submission requirement, not a suggestion.

### X. Faithful, Grounded AI Insight Generation

The AI-generated trend insight (brief Section 7) MUST have the actual historical rate data for
the exact selected currency pair and date range serialized into the LLM prompt as context — the
model MUST be responding to real, injected numbers, never guessing or falling back to a static or
templated sentence that is independent of the underlying data (brief Section 7.2, first bullet).
The system prompt MUST explicitly constrain the model's output to a short, relevant, non-generic
commentary — no boilerplate disclaimers, no financial-advice framing, no filler that would be
equally true of any dataset (brief Section 7.2, second bullet; graded explicitly under Section 10,
"Prompt design," 7%). The feature MUST degrade gracefully — a clear loading state while the
request is in flight, and a clear, distinct error state if the local model is unavailable or the
call fails — since reviewers running the repository may not have the required local model already
pulled (brief Section 7.2, third and fourth bullets).

**Rationale**: Collectively, Section 10 weights the AI Trend Insight category at 20% of the total
grade across three line items: "Spring AI wiring" (8%), "Prompt design" (7%: "rate data injected
as context, output constrained to relevant insight, not generic filler"), and "Frontend
integration" (5%: "insight displayed with loading state, endpoint design sensible").

## Technology Stack (Fixed, Non-Negotiable)

Per brief Section 3, the following choices are fixed by the assessment brief itself and MUST NOT
be changed without an explicit constitution amendment recording the deviation and its rationale:

- **Backend**: Java 17 or later, Spring Boot, Maven, Hibernate / Spring Data JPA, any relational
  database (local dev/test database choice is an implementation decision, not fixed by the
  brief).
- **Frontend**: Angular v15 or later, TypeScript throughout the application (no plain JavaScript
  files in application source).
- **AI Integration**: Spring AI (preferred by the brief) or LangChain4j, connected to any
  open-source LLM — a locally-running Ollama model, another local model, or any
  OpenAI-compatible endpoint. The specific model choice is an implementation decision.
- **AI Coding Tools**: At least one AI coding assistant (Claude Code, Cursor, GitHub Copilot, or
  equivalent) MUST be demonstrably part of the actual development workflow, not merely installed
  — see the AI-Augmented Workflow Requirements section below.
- **API Documentation**: Swagger / OpenAPI, auto-generated from the backend and browsable via a
  running Swagger UI instance.
- **External data source**: Fixer.io (free-tier subscription), fetched once per day per Principle
  III; rate lookups at request time MUST be served from locally stored data only (brief Section
  2: "so the application does not depend on the external API for every query").

Everything not listed above — project structure, specific libraries beyond these fixed choices,
internal patterns, the specific relational database engine, the specific local LLM model — is an
implementation decision to be recorded (with rationale where non-obvious) in `plan.md`.

## Assessment Deliverables & Submission Requirements

Per brief Section 9, the final submission MUST include, in addition to working software:

- A GitHub repository (public, or private with recruiter access granted) with a meaningful,
  legible commit history — not a single squashed "final" commit.
- A README covering: local setup and run instructions, an architecture overview, an "AI Workflow"
  section (see below), assumptions made, and known trade-offs.
- A 3–5 minute screen recording showing the running application in use and at least one AI agent
  session (live or narrated replay) — the brief states explicitly that "showing the process
  matters as much as showing the product."

## AI-Augmented Workflow Requirements

Per brief Section 8, the AI-Augmented Development category carries 25% of the total grade —
weighted equally with the entire Backend category — and the brief states explicitly (Section 10,
closing note) that "a candidate who completes all backend and frontend requirements but shows no
substantive AI workflow evidence cannot exceed 75%." The following are therefore treated as
first-class project deliverables, not optional nice-to-haves:

- A planning artifact (this Spec Kit's `plan.md`, or an equivalent `PLAN.md`) MUST exist, MUST be
  produced with genuine AI assistance, and MUST predate the implementation commits it describes
  (brief Section 8.2, item 1; Section 10, "Planning artefact," 5%).
- Committed AI tool configuration (this repository's `.claude/` directory, Spec Kit skills/
  commands, `CLAUDE.md`, or equivalent) MUST be substantive and reflect real, working
  configuration — an empty or placeholder configuration file is explicitly called out in the
  brief (Section 8.2, item 2) as "a signal that AI was used superficially" (Section 10, "Tool
  configuration," 8%).
- Commits that are primarily AI-generated or AI-assisted SHOULD use a consistent, searchable
  prefix (e.g., `[AI]`) so that AI-assisted phases of work are traceable in the commit history
  without requiring a reviewer to read every diff (brief Section 8.2, item 4).
- The README MUST include a section titled "AI Workflow" naming the tool(s) used, describing how
  they were configured, and describing at least one concrete instance where the agent produced
  output the developer disagreed with, plus what was done about it (brief Section 8.2, item 3;
  Section 10, "Critical AI use," 5%: "passive acceptance scores low").
- Test suites SHOULD be AI-generated first and then reviewed and corrected by a human before being
  trusted as a completeness signal (brief Section 8.1; Principle VII above).
- Evidence of multi-step, context-aware, iterated agent use (coherent multi-file outputs, agentic
  sessions) SHOULD be visible in the commit history and README, as opposed to isolated
  single-prompt snippet generation (brief Section 8.1 and 8.3; Section 10, "Agentic workflow
  evidence," 7%).

## Governance

This constitution supersedes ad-hoc practice for this repository for the duration of the
assessment. Amendments are intentionally lightweight given the project's scale and timeline:

- Any contributor (human or AI-assisted) MAY propose an amendment by editing this file directly.
- Every amendment MUST update the version number according to semantic versioning — MAJOR for a
  principle removal or redefinition that breaks prior compliance, MINOR for a new principle or
  materially expanded guidance, PATCH for wording or clarification only — and MUST update the
  `Last Amended` date, and MUST prepend an updated Sync Impact Report describing what changed and
  why.
- No formal review board or multi-approver process is required at this project's scale; a single,
  self-consistent commit updating this file is sufficient.
- `spec.md`, `plan.md`, `tasks.md`, and generated code SHOULD be checked against these principles
  at each Spec Kit phase transition (specify → plan → tasks → implement). Any deviation from a
  principle MUST be called out explicitly in `plan.md`'s Complexity Tracking section (or
  equivalent) with a stated justification, rather than being silently absorbed into the design.

**Version**: 1.0.0 | **Ratified**: 2026-08-21 | **Last Amended**: 2026-08-21
