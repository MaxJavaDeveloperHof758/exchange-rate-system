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
     * Idempotent, multi-instance-safe upsert keyed on the (currency_code, rate_date)
     * unique constraint (research.md Decision 3) — a single atomic H2 MERGE, not a
     * "select then decide insert/update" round trip. Inserts a new row with both
     * timestamps set, or updates rate_to_usd and updated_at in place while leaving
     * created_at untouched on an existing row.
     *
     * H2-specific shorthand MERGE syntax (not portable to PostgreSQL as written) —
     * accepted per constitution Principle VIII, since H2 is the only datasource this
     * assessment actually exercises (plan.md).
     */
    @Modifying
    @Transactional
    @Query(value = """
            MERGE INTO exchange_rate (currency_code, rate_to_usd, rate_date, created_at, updated_at)
            KEY (currency_code, rate_date)
            VALUES (
                :currencyCode,
                :rateToUsd,
                :rateDate,
                COALESCE(
                    (SELECT created_at FROM exchange_rate
                     WHERE currency_code = :currencyCode AND rate_date = :rateDate),
                    CURRENT_TIMESTAMP
                ),
                CURRENT_TIMESTAMP
            )
            """, nativeQuery = true)
    void upsert(
            @Param("currencyCode") String currencyCode,
            @Param("rateToUsd") BigDecimal rateToUsd,
            @Param("rateDate") LocalDate rateDate);
}
