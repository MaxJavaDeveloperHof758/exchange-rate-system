package com.exchange.exchangeratesystem.rate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByCurrencyCodeAndRateDate(String currencyCode, LocalDate rateDate);

    Optional<ExchangeRate> findTopByCurrencyCodeOrderByRateDateDesc(String currencyCode);

    List<ExchangeRate> findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
            String currencyCode, LocalDate start, LocalDate end);

    /**
     * The most recent date on which BOTH currencies have a stored rate —
     * deliberately not each currency's own {@code findTopByCurrencyCodeOrderByRateDateDesc}
     * compared against each other, which can silently pick a date only one
     * of the two actually has data for whenever their ingestion histories
     * diverge (e.g. one currency missing a day the other has). The EXISTS
     * subquery finds, among every date the first currency has, the latest
     * one the second currency also has — plain JPQL, no native/dialect-specific SQL.
     */
    @Query("""
            SELECT MAX(a.rateDate) FROM ExchangeRate a
            WHERE a.currencyCode = :fromCode
              AND EXISTS (
                  SELECT 1 FROM ExchangeRate b
                  WHERE b.currencyCode = :toCode AND b.rateDate = a.rateDate
              )
            """)
    Optional<LocalDate> findMostRecentCommonDate(
            @Param("fromCode") String fromCode, @Param("toCode") String toCode);

    /**
     * Idempotent, multi-instance-safe upsert keyed on the (currency_code, rate_date)
     * unique constraint (research.md Decision 3) — a single atomic Postgres
     * {@code INSERT ... ON CONFLICT DO UPDATE}, not a "select then decide
     * insert/update" round trip. Inserts a new row with both timestamps set to
     * {@code CURRENT_TIMESTAMP}, or updates rate_to_usd and updated_at in place;
     * created_at is deliberately absent from the DO UPDATE SET list, so an
     * existing row's created_at is left untouched on conflict — it only takes
     * the inserted value on an actual insert.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO exchange_rate (currency_code, rate_to_usd, rate_date, created_at, updated_at)
            VALUES (:currencyCode, :rateToUsd, :rateDate, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (currency_code, rate_date)
            DO UPDATE SET rate_to_usd = EXCLUDED.rate_to_usd, updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    void upsert(
            @Param("currencyCode") String currencyCode,
            @Param("rateToUsd") BigDecimal rateToUsd,
            @Param("rateDate") LocalDate rateDate);
}
