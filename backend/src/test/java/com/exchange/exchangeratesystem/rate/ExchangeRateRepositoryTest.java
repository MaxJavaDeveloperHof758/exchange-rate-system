package com.exchange.exchangeratesystem.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.sql.Savepoint;
import java.time.LocalDate;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.exchange.exchangeratesystem.support.PostgresTestContainerConfig;

/**
 * Confirms the (currency_code, rate_date) unique constraint from data-model.md is
 * enforced at the database level — not merely assumed in application code — by
 * attempting a duplicate direct insert through the plain repository, entirely
 * bypassing T010's native upsert, and asserting it is rejected.
 *
 * {@code @AutoConfigureTestDatabase(replace = NONE)} stops {@code @DataJpaTest}
 * from substituting its own auto-configured embedded database over the
 * imported Testcontainers Postgres — needed since production-path SQL here
 * (the upsert test below) is Postgres-specific and no longer runs on H2 at
 * all. Each test method's transaction is still rolled back afterward.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(PostgresTestContainerConfig.class)
class ExchangeRateRepositoryTest {

    @Autowired
    private ExchangeRateRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void duplicateCurrencyAndRateDateViolatesUniqueConstraint() {
        LocalDate rateDate = LocalDate.of(2026, 3, 15);
        repository.saveAndFlush(new ExchangeRate("EUR", new BigDecimal("0.80"), rateDate));

        // PostgreSQL aborts the entire surrounding transaction on ANY statement
        // error - confirmed empirically switching off H2, which tolerates
        // continuing after one - so every later statement on this connection
        // fails with "current transaction is aborted" until a rollback. A
        // savepoint taken just before the doomed insert scopes the abort to
        // that one statement, which is what a real caller (its own fresh
        // transaction) would actually observe instead.
        Session session = entityManager.unwrap(Session.class);
        Savepoint[] beforeDuplicateInsert = new Savepoint[1];
        session.doWork(connection -> beforeDuplicateInsert[0] = connection.setSavepoint());

        assertThatThrownBy(() ->
                repository.saveAndFlush(new ExchangeRate("EUR", new BigDecimal("0.90"), rateDate)))
                .isInstanceOf(DataIntegrityViolationException.class);

        session.doWork(connection -> connection.rollback(beforeDuplicateInsert[0]));

        // A failed flush also leaves the persistence context in a state where
        // the rejected, still-attached entity has no identifier — Hibernate
        // refuses any further operation on this session until it's cleared.
        // Detaching everything here is the standard recovery, not a
        // workaround specific to this test.
        entityManager.clear();

        // The rejected insert must not have corrupted the table: exactly one row
        // for this (currency, date) remains, holding the original value.
        assertThat(repository.findByCurrencyCodeAndRateDate("EUR", rateDate))
                .get()
                .extracting(ExchangeRate::getRateToUsd)
                .satisfies(rate -> assertThat(rate).isEqualByComparingTo(new BigDecimal("0.80")));
    }

    /**
     * The DECIMAL(19,10) column width is a raw {@code columnDefinition} string
     * (data-model.md), which Hibernate's {@code ddl-auto=validate} does not
     * structurally check against the DB — a Flyway migration declaring a
     * narrower column (e.g. DECIMAL(10,2)) still passes validation and every
     * other test here, since none of them exercise more than 2 decimal
     * digits. This test is the actual guard against that: it round-trips a
     * value using the full 10-digit scale and would fail on silent
     * truncation, which "ddl-auto=validate says it's fine" alone would not
     * catch.
     */
    @Test
    void rateToUsdRoundTripsAtFullDecimalPrecision() {
        LocalDate rateDate = LocalDate.of(2026, 3, 15);
        BigDecimal fullPrecisionRate = new BigDecimal("1.2345678901");
        repository.saveAndFlush(new ExchangeRate("EUR", fullPrecisionRate, rateDate));
        entityManager.clear();

        assertThat(repository.findByCurrencyCodeAndRateDate("EUR", rateDate))
                .get()
                .extracting(ExchangeRate::getRateToUsd)
                .satisfies(rate -> assertThat(rate).isEqualByComparingTo(fullPrecisionRate));
    }

    /**
     * The Postgres {@code ON CONFLICT DO UPDATE} rewrite (formerly an H2
     * {@code MERGE}) has no prior direct test — every existing caller only
     * exercises it indirectly through a service. This pins the exact
     * behavior the upsert's Javadoc claims: a fresh row gets both timestamps
     * set to "now," and a later conflicting upsert updates rate_to_usd/
     * updated_at while leaving the original created_at untouched (it is
     * deliberately absent from the SQL's DO UPDATE SET list).
     */
    @Test
    void upsertInsertsFreshRowThenUpdatesRateWhilePreservingCreatedAt() {
        LocalDate rateDate = LocalDate.of(2026, 3, 15);

        repository.upsert("EUR", new BigDecimal("0.85"), rateDate);
        entityManager.flush();
        entityManager.clear();

        ExchangeRate inserted = repository.findByCurrencyCodeAndRateDate("EUR", rateDate).get();
        assertThat(inserted.getRateToUsd()).isEqualByComparingTo(new BigDecimal("0.85"));
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(inserted.getUpdatedAt()).isNotNull();

        repository.upsert("EUR", new BigDecimal("0.90"), rateDate);
        entityManager.flush();
        entityManager.clear();

        ExchangeRate updated = repository.findByCurrencyCodeAndRateDate("EUR", rateDate).get();
        assertThat(updated.getRateToUsd()).isEqualByComparingTo(new BigDecimal("0.90"));
        assertThat(updated.getCreatedAt()).isEqualTo(inserted.getCreatedAt());
        assertThat(repository.count()).isEqualTo(1);
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
