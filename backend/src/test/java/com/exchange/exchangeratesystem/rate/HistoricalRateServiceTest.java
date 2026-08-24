package com.exchange.exchangeratesystem.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

import com.exchange.exchangeratesystem.currency.CurrencySpreadTestFixtures;
import com.exchange.exchangeratesystem.error.RateNotAvailableException;
import com.exchange.exchangeratesystem.rate.dto.HistoricalRatePoint;
import com.exchange.exchangeratesystem.rate.dto.HistoryResponse;
import com.exchange.exchangeratesystem.support.PostgresTestContainerConfig;

/**
 * Covers the series-building logic previously duplicated between
 * {@code ExchangeRateController#getHistory} and
 * {@code TrendInsightService#buildSeries} — now consolidated here as the
 * one implementation both {@code ExchangeRateController} and
 * {@code InsightController} call. {@code SpreadCalculationService} is the
 * real implementation (not mocked), so asserted rates are the actual
 * formula output.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(PostgresTestContainerConfig.class)
class HistoricalRateServiceTest {

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    private HistoricalRateService service;

    @BeforeEach
    void setUp() {
        service = new HistoricalRateService(
                exchangeRateRepository, new SpreadCalculationService(CurrencySpreadTestFixtures.realAppendixB()));
    }

    @Test
    void sameCurrencyProducesAConstantOneForEveryDateInRangeWithNoDbLookup() {
        List<HistoricalRatePoint> points =
                service.computeSeries(
                        "EUR", "EUR", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));

        assertThat(points).hasSize(3);
        assertThat(points)
                .extracting(HistoricalRatePoint::adjustedRate)
                .allSatisfy(rate -> assertThat(rate).isEqualByComparingTo(BigDecimal.ONE));
        assertThat(points)
                .as("no DB lookup is performed for a same-currency pair, so there is no raw rate to report")
                .allSatisfy(point -> {
                    assertThat(point.fromRateToUsd()).isNull();
                    assertThat(point.toRateToUsd()).isNull();
                });
    }

    @Test
    void computeSeriesOmitsDatesMissingDataForEitherCurrency() {
        exchangeRateRepository.upsert("EUR", new BigDecimal("0.80"), LocalDate.of(2026, 3, 1));
        exchangeRateRepository.upsert("PLN", new BigDecimal("3.70"), LocalDate.of(2026, 3, 1));
        // Day 2: only EUR has data - PLN is missing, so day 2 must be omitted entirely,
        // not silently paired with stale/wrong data.
        exchangeRateRepository.upsert("EUR", new BigDecimal("0.81"), LocalDate.of(2026, 3, 2));

        List<HistoricalRatePoint> points =
                service.computeSeries(
                        "EUR", "PLN", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2));

        assertThat(points).extracting(HistoricalRatePoint::date)
                .containsExactly(LocalDate.of(2026, 3, 1));
    }

    @Test
    void getHistoryListsMissingDatesForAPartiallyIncompleteRangeInsteadOfFailing() {
        exchangeRateRepository.upsert("EUR", new BigDecimal("0.80"), LocalDate.of(2026, 3, 1));
        exchangeRateRepository.upsert("PLN", new BigDecimal("3.70"), LocalDate.of(2026, 3, 1));
        exchangeRateRepository.upsert("EUR", new BigDecimal("0.81"), LocalDate.of(2026, 3, 3));
        exchangeRateRepository.upsert("PLN", new BigDecimal("3.75"), LocalDate.of(2026, 3, 3));

        HistoryResponse response =
                service.getHistory("EUR", "PLN", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));

        assertThat(response.points())
                .extracting(HistoricalRatePoint::date)
                .containsExactly(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));
        assertThat(response.missingDates()).containsExactly(LocalDate.of(2026, 3, 2));
    }

    @Test
    void computeSeriesReportsEachCurrencysOwnRawStoredRateAlongsideTheAdjustedPairRate() {
        LocalDate day = LocalDate.of(2026, 3, 15);
        BigDecimal eurRate = new BigDecimal("0.80");
        BigDecimal plnRate = new BigDecimal("3.70");
        exchangeRateRepository.upsert("EUR", eurRate, day);
        exchangeRateRepository.upsert("PLN", plnRate, day);

        List<HistoricalRatePoint> points = service.computeSeries("EUR", "PLN", day, day);

        assertThat(points).hasSize(1);
        HistoricalRatePoint point = points.get(0);
        // FR-014: the raw rates actually stored for each currency, not just
        // the derived pair rate, must be reported.
        assertThat(point.fromRateToUsd()).isEqualByComparingTo(eurRate);
        assertThat(point.toRateToUsd()).isEqualByComparingTo(plnRate);
        assertThat(point.adjustedRate())
                .isEqualByComparingTo(
                        new SpreadCalculationService(CurrencySpreadTestFixtures.realAppendixB())
                                .calculate("PLN", "EUR", plnRate, eurRate));
    }

    @Test
    void getHistoryThrowsWhenNoDataExistsAnywhereInRange() {
        assertThatThrownBy(
                        () ->
                                service.getHistory(
                                        "EUR", "PLN", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)))
                .isInstanceOf(RateNotAvailableException.class);
    }
}
