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
}
