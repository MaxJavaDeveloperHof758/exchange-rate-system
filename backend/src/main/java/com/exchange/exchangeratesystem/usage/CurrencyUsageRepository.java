package com.exchange.exchangeratesystem.usage;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CurrencyUsageRepository extends JpaRepository<CurrencyUsage, String> {

    List<CurrencyUsage> findAllByOrderByQueryCountDesc();

    /**
     * Atomically increments an EXISTING currency's query_count by exactly 1 and
     * advances last_queried_date, via a plain single-row UPDATE (research.md
     * Decision 5) — never a read-entity/increment-in-Java/save-entity round trip.
     * Returns the number of rows affected: 1 if the currency already had a row,
     * 0 if it did not (first-ever lookup for that currency) — in which case the
     * caller (UsageTrackingService, T020) is responsible for creating the row,
     * e.g. via {@code save(new CurrencyUsage(...))}, and for retrying as an
     * update if that insert loses a race against another thread's simultaneous
     * first-ever insert of the same brand-new currency (catch the resulting
     * DataIntegrityViolationException on the currency_code primary key).
     *
     * This is deliberately UPDATE-only, not a single upsert-and-increment
     * statement as T011 originally specified. Two MERGE-based single-statement
     * forms were tried and both failed under a 50-concurrent-thread test:
     * (1) H2's shorthand "MERGE INTO ... KEY(...) VALUES (..., (SELECT
     * query_count ...) + 1, ...)" reads the current count via an independent,
     * unlocked subquery before writing, so concurrent calls raced and lost
     * updates (measured: 50 concurrent calls on one currency produced a final
     * count of 8, not 50). (2) The ANSI-standard "MERGE ... WHEN MATCHED THEN
     * UPDATE SET query_count = query_count + 1 ... WHEN NOT MATCHED THEN
     * INSERT ..." form fixed that — the matched/update branch is genuinely
     * atomic — but its NOT MATCHED/insert branch still raced when many threads
     * hit the same brand-new currency at once, each seeing "not matched" and
     * colliding on the primary key (measured: 9 of 50 threads failed with a
     * DataIntegrityViolationException, final count 41, not 50). A plain UPDATE
     * against an existing row has neither failure mode — confirmed by the same
     * 50-concurrent-thread test producing exactly 50 once row creation was
     * moved out of this method.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE currency_usage
            SET query_count = query_count + 1, last_queried_date = :queriedDate
            WHERE currency_code = :currencyCode
            """, nativeQuery = true)
    int incrementUsage(
            @Param("currencyCode") String currencyCode,
            @Param("queriedDate") LocalDate queriedDate);

    /**
     * Creates the row for a currency's first-ever lookup, with query_count = 1
     * (this lookup itself) — not 0. Deliberately a native INSERT, like
     * {@link #incrementUsage}, rather than {@code save(new CurrencyUsage(...))}:
     * mixing an ORM-managed save with a native bulk query in the same
     * transaction risks the Hibernate session's first-level cache holding a
     * stale in-memory view of a row a native query changed underneath it.
     * Keeping both write paths pure-native/JDBC, with no entity ever attached
     * to a persistence context, sidesteps that entirely. Throws
     * {@code DataIntegrityViolationException} on the currency_code primary key
     * if another thread's insert of the same brand-new currency wins the race —
     * the caller (UsageTrackingService) retries as an {@link #incrementUsage}
     * call when that happens.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO currency_usage (currency_code, query_count, last_queried_date)
            VALUES (:currencyCode, 1, :queriedDate)
            """, nativeQuery = true)
    void insertNewRow(
            @Param("currencyCode") String currencyCode,
            @Param("queriedDate") LocalDate queriedDate);

    /**
     * The exact inverse of {@link #incrementUsage} — used only by
     * {@code UsageTrackingService} to compensate a currency whose count it
     * already incremented, when a pair lookup's other currency then fails.
     * Equally atomic and concurrency-safe: a relative {@code -1} composes
     * correctly with any other request's concurrent increment/decrement of
     * the same currency, the same way {@code incrementUsage}'s relative
     * {@code +1} does. Never produces a negative count: the only caller
     * always decrements a currency it just incremented by exactly 1 moments
     * earlier, so the row's count is always >= 1 at this point.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE currency_usage
            SET query_count = query_count - 1
            WHERE currency_code = :currencyCode
            """, nativeQuery = true)
    int decrementUsage(@Param("currencyCode") String currencyCode);

    /**
     * Cleans up a currency's row after {@link #decrementUsage} compensated
     * away its very first-ever (and, so far, only) recorded lookup, so a
     * fully-compensated pair lookup doesn't leave a lingering
     * {@code query_count = 0} row — contracts/analytics.md is explicit that
     * a currency never successfully queried must not appear in
     * {@code /api/analytics} at all, not appear with a count of zero.
     * {@code WHERE query_count = 0} is evaluated atomically against the
     * row's live state, so if another request concurrently incremented this
     * same currency again in between, this simply deletes nothing — that
     * request's own count is left correctly intact.
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM currency_usage WHERE currency_code = :currencyCode AND query_count = 0",
            nativeQuery = true)
    void deleteIfZeroCount(@Param("currencyCode") String currencyCode);
}
