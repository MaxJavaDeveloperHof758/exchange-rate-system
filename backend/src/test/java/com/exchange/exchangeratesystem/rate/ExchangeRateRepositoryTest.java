package com.exchange.exchangeratesystem.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Confirms the (currency_code, rate_date) unique constraint from data-model.md is
 * enforced at the database level — not merely assumed in application code — by
 * attempting a duplicate direct insert through the plain repository, entirely
 * bypassing T010's native upsert, and asserting it is rejected.
 *
 * {@code @DataJpaTest} replaces the configured H2 file datasource with an
 * isolated, auto-configured embedded H2 for these tests (its default behavior),
 * so this never touches the real backend/data/exchangedb file, and each test
 * method's transaction is rolled back afterward.
 */
@DataJpaTest
class ExchangeRateRepositoryTest {

    @Autowired
    private ExchangeRateRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void duplicateCurrencyAndRateDateViolatesUniqueConstraint() {
        LocalDate rateDate = LocalDate.of(2026, 3, 15);
        repository.saveAndFlush(new ExchangeRate("EUR", new BigDecimal("0.80"), rateDate));

        assertThatThrownBy(() ->
                repository.saveAndFlush(new ExchangeRate("EUR", new BigDecimal("0.90"), rateDate)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // A failed flush leaves the persistence context in a state where the
        // rejected, still-attached entity has no identifier — Hibernate refuses
        // any further operation on this session until it's cleared. Detaching
        // everything here is the standard recovery, not a workaround specific
        // to this test.
        entityManager.clear();

        // The rejected insert must not have corrupted the table: exactly one row
        // for this (currency, date) remains, holding the original value.
        assertThat(repository.findByCurrencyCodeAndRateDate("EUR", rateDate))
                .get()
                .extracting(ExchangeRate::getRateToUsd)
                .satisfies(rate -> assertThat(rate).isEqualByComparingTo(new BigDecimal("0.80")));
    }

    @Test
    void sameCurrencyDifferentRateDateIsAllowed() {
        repository.saveAndFlush(
                new ExchangeRate("EUR", new BigDecimal("0.80"), LocalDate.of(2026, 3, 15)));
        repository.saveAndFlush(
                new ExchangeRate("EUR", new BigDecimal("0.81"), LocalDate.of(2026, 3, 16)));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void sameRateDateDifferentCurrencyIsAllowed() {
        LocalDate rateDate = LocalDate.of(2026, 3, 15);
        repository.saveAndFlush(new ExchangeRate("EUR", new BigDecimal("0.80"), rateDate));
        repository.saveAndFlush(new ExchangeRate("PLN", new BigDecimal("3.70"), rateDate));

        assertThat(repository.count()).isEqualTo(2);
    }

    /**
     * Both currencies' own most-recent date happens to coincide — the
     * simple case a naive "compare each currency's own latest date"
     * approach also gets right.
     */
    @Test
    void findMostRecentCommonDateReturnsSharedLatestDateWhenHistoriesAlign() {
        seed("EUR", LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 9));
        seed("PLN", LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 8));

        assertThat(repository.findMostRecentCommonDate("EUR", "PLN"))
                .contains(LocalDate.of(2026, 3, 10));
    }

    /**
     * Reproduces the divergent-history case a "min of each currency's own
     * latest date" approach gets wrong: EUR's own latest (10th) has no PLN
     * data at all, and PLN's own latest (9th) has no EUR data either — the
     * naive approach would pick the 9th (min(10, 9)) and 404, even though a
     * real common date (the 7th) exists further back. The correct answer
     * must actually be found, not merely fail safely.
     */
    @Test
    void findMostRecentCommonDateSkipsPastNonCommonLatestDates() {
        seed("EUR", LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 7));
        seed("PLN", LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 7));

        assertThat(repository.findMostRecentCommonDate("EUR", "PLN"))
                .contains(LocalDate.of(2026, 3, 7));
    }

    @Test
    void findMostRecentCommonDateIsEmptyWhenNoDateOverlapsAtAll() {
        seed("EUR", LocalDate.of(2026, 3, 10));
        seed("PLN", LocalDate.of(2026, 3, 9));

        assertThat(repository.findMostRecentCommonDate("EUR", "PLN")).isEmpty();
    }

    private void seed(String currencyCode, LocalDate... rateDates) {
        for (LocalDate rateDate : rateDates) {
            repository.saveAndFlush(new ExchangeRate(currencyCode, new BigDecimal("1.00"), rateDate));
        }
    }
}
