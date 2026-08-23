package com.exchange.exchangeratesystem.web;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import com.exchange.exchangeratesystem.currency.CurrencyCode;
import com.exchange.exchangeratesystem.error.InvalidDateRangeException;
import com.exchange.exchangeratesystem.error.RateNotAvailableException;
import com.exchange.exchangeratesystem.error.UnknownCurrencyException;
import com.exchange.exchangeratesystem.insight.TrendInsightService;
import com.exchange.exchangeratesystem.insight.dto.InsightResponse;
import com.exchange.exchangeratesystem.rate.ExchangeRate;
import com.exchange.exchangeratesystem.rate.ExchangeRateRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/exchange/insight} — the AI-generated trend commentary (T037,
 * User Story 2's AI half), per contracts/insight.md. A separate controller
 * from {@link ExchangeRateController} despite sharing the {@code /api/exchange}
 * path prefix, per T037's own file layout. Never reads or writes any Currency
 * Usage Counter row (FR-011's scope is {@code /api/exchange} lookups only).
 */
@RestController
@RequestMapping("/api/exchange")
public class InsightController {

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyCode currencyCode;
    private final TrendInsightService trendInsightService;

    public InsightController(
            ExchangeRateRepository exchangeRateRepository,
            CurrencyCode currencyCode,
            TrendInsightService trendInsightService) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.currencyCode = currencyCode;
        this.trendInsightService = trendInsightService;
    }

    @GetMapping("/insight")
    public InsightResponse getInsight(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam LocalDate fromDate,
            @RequestParam LocalDate toDate) {
        String fromCode = from.toUpperCase();
        String toCode = to.toUpperCase();
        validateCurrency(fromCode);
        validateCurrency(toCode);
        if (fromDate.isAfter(toDate)) {
            throw new InvalidDateRangeException(
                    "fromDate " + fromDate + " is after toDate " + toDate);
        }
        if (!hasAnyDataInRange(fromCode, toCode, fromDate, toDate)) {
            throw new RateNotAvailableException(
                    "No rate data available for " + fromCode + "/" + toCode + " between "
                            + fromDate + " and " + toDate);
        }

        String insight = trendInsightService.generateInsight(fromCode, toCode, fromDate, toDate);
        return new InsightResponse(fromCode, toCode, fromDate, toDate, insight);
    }

    private void validateCurrency(String code) {
        if (!currencyCode.isSupported(code)) {
            throw new UnknownCurrencyException("Unknown currency code: " + code);
        }
    }

    /**
     * Whether there is anything at all to summarize for this pair/range —
     * reuses T009's range query (same repository method
     * {@code ExchangeRateController#getHistory} uses) rather than building
     * the full spread-adjusted series, since only existence, not the series
     * itself, is needed here; {@link TrendInsightService} builds its own
     * series independently once this returns true. A same-currency pair
     * always has data (a constant 1, per {@code getExchangeRate}/
     * {@code getHistory}'s same-currency handling), regardless of what is
     * actually stored.
     */
    private boolean hasAnyDataInRange(String from, String to, LocalDate fromDate, LocalDate toDate) {
        if (from.equalsIgnoreCase(to)) {
            return true;
        }
        Set<LocalDate> fromDates = datesWithData(from, fromDate, toDate);
        Set<LocalDate> toDates = datesWithData(to, fromDate, toDate);
        return fromDates.stream().anyMatch(toDates::contains);
    }

    private Set<LocalDate> datesWithData(String code, LocalDate startDate, LocalDate endDate) {
        Set<LocalDate> dates = new HashSet<>();
        for (ExchangeRate rate :
                exchangeRateRepository.findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
                        code, startDate, endDate)) {
            dates.add(rate.getRateDate());
        }
        return dates;
    }
}
