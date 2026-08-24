package com.exchange.exchangeratesystem.rate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.exchange.exchangeratesystem.error.RateNotAvailableException;
import com.exchange.exchangeratesystem.rate.dto.HistoricalRatePoint;
import com.exchange.exchangeratesystem.rate.dto.HistoryResponse;

/**
 * The single, canonical implementation of "the spread-adjusted (date, rate)
 * series for a currency pair over a date range" — used by both
 * {@code ExchangeRateController#getHistory} and {@code InsightController}
 * (which passes the resulting series into {@code TrendInsightService} for
 * prompt construction). Previously each of those built this same series
 * independently, with the exact same logic duplicated across two files — a
 * genuine duplication-bug risk (a future change to the same-currency rule or
 * the missing-data handling would have had to be made in both places, or
 * silently diverge). {@link #computeSeries} is that one implementation;
 * {@link #getHistory} adds the {@code missingDates}/404-on-empty response
 * shaping that only {@code GET /api/exchange/history} itself needs.
 */
@Service
public class HistoricalRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final SpreadCalculationService spreadCalculationService;

    public HistoricalRateService(
            ExchangeRateRepository exchangeRateRepository,
            SpreadCalculationService spreadCalculationService) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.spreadCalculationService = spreadCalculationService;
    }

    /**
     * The spread-adjusted rate for every date in {@code [startDate, endDate]}
     * that has usable stored data for both currencies, in ascending date
     * order; a date with no usable data for one or both currencies is simply
     * absent from the result (never a failure by itself — see
     * {@link #getHistory} for when "nothing at all" becomes one). A
     * same-currency pair always returns a constant {@code 1} for every date
     * in range, with no DB lookup at all, consistent with
     * {@code ExchangeRateQueryService}'s same-currency handling.
     */
    public List<HistoricalRatePoint> computeSeries(
            String fromCode, String toCode, LocalDate startDate, LocalDate endDate) {
        List<HistoricalRatePoint> points = new ArrayList<>();

        if (fromCode.equalsIgnoreCase(toCode)) {
            for (LocalDate cursor = startDate; !cursor.isAfter(endDate); cursor = cursor.plusDays(1)) {
                points.add(new HistoricalRatePoint(cursor, null, null, BigDecimal.ONE));
            }
            return points;
        }

        Map<LocalDate, BigDecimal> fromRates = ratesByDate(fromCode, startDate, endDate);
        Map<LocalDate, BigDecimal> toRates = ratesByDate(toCode, startDate, endDate);

        for (LocalDate cursor = startDate; !cursor.isAfter(endDate); cursor = cursor.plusDays(1)) {
            BigDecimal fromRateToUsd = fromRates.get(cursor);
            BigDecimal toRateToUsd = toRates.get(cursor);
            if (fromRateToUsd != null && toRateToUsd != null) {
                BigDecimal adjustedRate = spreadCalculationService.calculate(
                        toCode, fromCode, toRateToUsd, fromRateToUsd);
                points.add(new HistoricalRatePoint(cursor, fromRateToUsd, toRateToUsd, adjustedRate));
            }
        }
        return points;
    }

    /**
     * {@code GET /api/exchange/history}'s full response: {@link #computeSeries}'s
     * points, plus every date in range that isn't one of them listed in
     * {@code missingDates} — a partially incomplete range is still a 200
     * (spec.md User Story 2, Acceptance Scenario 5) — and a
     * {@link RateNotAvailableException} (404) only when there is nothing
     * usable anywhere in the range at all.
     */
    public HistoryResponse getHistory(
            String fromCode, String toCode, LocalDate startDate, LocalDate endDate) {
        List<HistoricalRatePoint> points = computeSeries(fromCode, toCode, startDate, endDate);
        if (points.isEmpty()) {
            throw new RateNotAvailableException(
                    "No rate data available for " + fromCode + "/" + toCode + " between "
                            + startDate + " and " + endDate);
        }
        List<LocalDate> missingDates = missingDatesIn(points, startDate, endDate);
        return new HistoryResponse(fromCode, toCode, startDate, endDate, points, missingDates);
    }

    private List<LocalDate> missingDatesIn(
            List<HistoricalRatePoint> points, LocalDate startDate, LocalDate endDate) {
        Set<LocalDate> present = new HashSet<>();
        for (HistoricalRatePoint point : points) {
            present.add(point.date());
        }
        List<LocalDate> missing = new ArrayList<>();
        for (LocalDate cursor = startDate; !cursor.isAfter(endDate); cursor = cursor.plusDays(1)) {
            if (!present.contains(cursor)) {
                missing.add(cursor);
            }
        }
        return missing;
    }

    private Map<LocalDate, BigDecimal> ratesByDate(String code, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, BigDecimal> byDate = new HashMap<>();
        for (ExchangeRate rate :
                exchangeRateRepository.findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
                        code, startDate, endDate)) {
            byDate.put(rate.getRateDate(), rate.getRateToUsd());
        }
        return byDate;
    }
}
