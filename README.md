# Exchange Rate Management System

> **Status**: Placeholder — this README will be filled in as implementation proceeds. See
> `specs/001-exchange-rate-management/` for the full specification, plan, and task breakdown, and
> `.specify/memory/constitution.md` for the project's engineering principles.

## Repository Layout

This is a monorepo — one Git repository holding both the backend and the frontend as sibling
top-level folders, alongside the Spec Kit planning documentation:

```text
.
├── backend/    # Spring Boot API (Java, Maven) — see backend/pom.xml
│                 currently a bare skeleton: pom.xml + empty src/main, src/test
├── frontend/   # Angular SPA — not yet scaffolded (frontend/.gitkeep holds the folder in Git)
├── specs/      # Spec Kit feature specification, plan, tasks, contracts
│                 → specs/001-exchange-rate-management/
├── .specify/   # Spec Kit configuration and templates
└── .claude/    # AI tool configuration (Claude Code skills)
```

`backend/` and `frontend/` are independently buildable/runnable (`mvn` from `backend/`, `ng` from
`frontend/` once scaffolded) — there is no shared build tooling between them, per this project's
constitution (`.specify/memory/constitution.md`, Principle VIII).

## Setup & Run

_TODO: document local setup once the backend/frontend are implemented — see
[`specs/001-exchange-rate-management/quickstart.md`](specs/001-exchange-rate-management/quickstart.md)
for the target validation steps this section should eventually cover._

## Architecture Overview

_TODO: summarize the architecture — see
[`specs/001-exchange-rate-management/plan.md`](specs/001-exchange-rate-management/plan.md) for the
full design (this section should be updated to reflect the `backend/`/`frontend/` monorepo layout,
since `plan.md`'s Project Structure was written before that split)._

## AI Workflow

_TODO: name the AI coding tool(s) used, how they were configured (see `.claude/` and
`CLAUDE.md`), and at least one concrete example of overriding/correcting AI-generated output —
required by the assessment brief._

## Assumptions

_TODO: see [`specs/001-exchange-rate-management/spec.md`](specs/001-exchange-rate-management/spec.md)'s
Assumptions section for the current list; restate the ones still relevant here once implementation
settles._

## Known Trade-offs

_TODO: document scope cuts and their rationale — see `plan.md`'s Complexity Tracking section for a
starting point._
