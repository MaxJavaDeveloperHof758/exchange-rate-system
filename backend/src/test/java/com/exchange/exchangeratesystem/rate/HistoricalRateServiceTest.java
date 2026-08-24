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

import com.exchange.exchangeratesystem.currency.CurrencySpread;
import com.exchange.exchangeratesystem.error.RateNotAvailableException;
import com.exchange.exchangeratesystem.rate.dto.HistoricalRatePoint;
import com.exchange.exchangeratesystem.rate.dto.HistoryResponse;

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
class HistoricalRateServiceTest {

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    private HistoricalRateService service;

    @BeforeEach
    void setUp() {
        service = new HistoricalRateService(
                exchangeRateRepository, new SpreadCalculationService(new CurrencySpread()));
    }

    @Test
    void sameCurrencyProducesAConstantOneForEveryDateInRangeWithNoDbLookup() {
        List<HistoricalRatePoint> points =
                service.computeSeries(
                        "EUR", "EUR", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));

        assertThat(points).hasSize(3);
        assertThat(points)
                .extracting(HistoricalRatePoint::exchange)
                .allSatisfy(rate -> assertThat(rate).isEqualByComparingTo(BigDecimal.ONE));
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
    void getHistoryThrowsWhenNoDataExistsAnywhereInRange() {
        assertThatThrownBy(
                        () ->
                                service.getHistory(
                                        "EUR", "PLN", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)))
                .isInstanceOf(RateNotAvailableException.class);
    }
}
