# Architecture Decision Records

Decision history that used to live inline as development-process narrative in code comments
("attempt 1 failed because...", "measured: ..."). Production code comments should explain a
non-obvious *invariant* or *trade-off* a maintainer needs to not break; the *journey* to that
invariant belongs here instead. See `specs/001-exchange-rate-management/implementation-log.md`
for the original build's task-by-task history, and
`specs/001-exchange-rate-management/adjustments-log.md` for the post-submission adjustments effort
this ADR was itself extracted during (that effort's own item 9).

---

## ADR-0001: Cross-currency usage-counter concurrency design

**Status**: Accepted.

**Context**: Each currency's usage counter (`currency_usage.query_count`) must increment
atomically under concurrent load, without a read-modify-write round trip in application code
(constitution Principle IV). The obvious-looking design is a single upsert-and-increment
statement — insert a fresh row with count 1, or increment an existing row's count, in one
atomic operation.

**What was tried and rejected**:

1. **H2's shorthand `MERGE INTO ... KEY(...) VALUES (..., (SELECT query_count ...) + 1, ...)`.**
   The `+1` reads the current count via an independent, unlocked subquery before writing, so
   concurrent calls raced and lost updates. Measured: 50 concurrent calls against one currency
   produced a final count of 8, not 50.
2. **ANSI-standard `MERGE ... WHEN MATCHED THEN UPDATE SET query_count = query_count + 1 ...
   WHEN NOT MATCHED THEN INSERT ...`.** This fixed the matched/update branch — genuinely atomic —
   but the `NOT MATCHED`/insert branch still raced when many threads hit the same brand-new
   currency at once, each seeing "not matched" and colliding on the primary key. Measured: 9 of 50
   threads failed with `DataIntegrityViolationException`, final count 41, not 50.
3. **A single DB transaction spanning both currencies of a pair lookup, using
   `PROPAGATION_NESTED` savepoints**, as the fix for cross-currency atomicity (see ADR-0002's
   context below) — rejected after failing empirically: Hibernate's own `Session`/`Transaction`
   does not support nested transactions at all on this project's actual Hibernate/Spring stack. A
   savepoint rolls back the JDBC connection cleanly, but Hibernate still marks the *whole*
   transaction rollback-only, so the outer commit then fails with `UnexpectedRollbackException`
   even on the success path.

**Decision**: Split into two separate, atomic native queries —
`CurrencyUsageRepository#incrementUsage` (plain `UPDATE ... WHERE currency_code = ?`, returns rows
affected) and `#insertNewRow` (plain `INSERT`, used only when `incrementUsage` reports 0 rows
affected). A plain `UPDATE` against an existing row has neither of the two failure modes above —
confirmed by the same 50-concurrent-thread test producing exactly 50 once row creation was moved
out of that one statement. `UsageTrackingService#recordSingleCurrencyLookup` retries as an update
(up to 3 attempts) if `insertNewRow` loses a race against another thread's simultaneous first
insert of the same brand-new currency.

For cross-currency atomicity (a pair lookup increments up to two currencies; if the second fails
after exhausting retries, the first's already-committed increment must not be left permanently
applied), the rejected nested-transaction approach was replaced with explicit compensation:
`UsageTrackingService#recordLookup` tracks which currencies it already recorded, and on failure
calls `CurrencyUsageRepository#decrementUsage`/`#deleteIfZeroCount` to undo them.

**Consequences**: Each currency's increment attempt runs in its own `PROPAGATION_REQUIRES_NEW`
transaction (via `TransactionTemplate`, not `@Transactional` on a same-class method — Spring's
proxy-based AOP does not intercept self-invocation). The compensation path is not transactional
with the original failure; a crash between the failure and the compensating decrement would leave
a currency's count 1 higher than it should be — accepted as a narrow, logged-but-unrecovered edge
case rather than added complexity for a probability-near-zero window.

**Re-verified, not assumed to transfer**: when the datasource moved from H2 to PostgreSQL
(adjustments-log.md item 6), the 50-concurrent-thread test was re-run against real Postgres
(via Testcontainers) rather than trusting the H2-proven result — passed identically, run 4 times.

**Where the code lives**: `CurrencyUsageRepository#incrementUsage`/`#insertNewRow`/
`#decrementUsage`/`#deleteIfZeroCount`, `UsageTrackingService#recordLookup`/
`#recordSingleCurrencyLookup`/`#compensate`.

---

## ADR-0002: Distributed lock for the scheduled ingestion job (ShedLock)

**Status**: Accepted — reverses an earlier decision.

**Context**: `plan.md`'s Complexity Tracking table originally decided against a distributed lock
library for the daily ingestion scheduler, for simplicity: the DB unique constraint + upsert
already guarantee correct, non-duplicated *stored* data even if multiple instances each ran the
job concurrently — a redundant `Fixer.io` call is wasteful, not a correctness bug.

**Decision**: Added anyway, on explicit user request during the post-submission adjustments
effort (adjustments-log.md item 1) — `net.javacrumbs.shedlock` (`shedlock-spring` +
`shedlock-provider-jdbc-template`, 7.9.0, the line with published Spring Boot 4/Spring Framework 7
compatibility), `@EnableSchedulerLock` + a `JdbcTemplateLockProvider` bean
(`SchedulingConfig`), `@SchedulerLock` on `RateIngestionScheduler#ingestDailyRates`.

**Consequences**: One additional table (`shedlock`, now a Flyway migration — see item 6) and two
new dependencies for a property (redundant-provider-call avoidance) that was already
non-critical for data correctness. Accepted as the user's explicit call, with the tension against
the original simplicity decision flagged rather than silently overridden.

**Where the code lives**: `SchedulingConfig`, `RateIngestionScheduler#ingestDailyRates`,
`RateIngestionSchedulerLockTest` (proves actual mutual exclusion between two concurrent calls).
