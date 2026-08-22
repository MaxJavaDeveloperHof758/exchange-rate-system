package com.exchange.exchangeratesystem.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.exchange.exchangeratesystem.currency.CurrencyCode;
import com.exchange.exchangeratesystem.error.InvalidDateRangeException;
import com.exchange.exchangeratesystem.error.RateNotAvailableException;
import com.exchange.exchangeratesystem.error.UnknownCurrencyException;
import com.exchange.exchangeratesystem.error.UpstreamFetchException;
import com.exchange.exchangeratesystem.rate.ExchangeRate;
import com.exchange.exchangeratesystem.rate.ExchangeRateRepository;
import com.exchange.exchangeratesystem.rate.FixerClientException;
import com.exchange.exchangeratesystem.rate.RateIngestionService;
import com.exchange.exchangeratesystem.rate.SpreadCalculationService;
import com.exchange.exchangeratesystem.rate.dto.ExchangeRateResponse;
import com.exchange.exchangeratesystem.rate.dto.HistoricalRatePoint;
import com.exchange.exchangeratesystem.rate.dto.HistoryResponse;
import com.exchange.exchangeratesystem.rate.dto.RefreshResponse;
import com.exchange.exchangeratesystem.usage.CurrencyUsageRepository;
import com.exchange.exchangeratesystem.usage.UsageTrackingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/exchange} — the spread-adjusted rate lookup (T027, User Story 1),
 * the historical range query (T027, User Story 2's raw-data half), and the
 * optional manual-refresh trigger (T028, FR-022). All three per contracts/exchange.md.
 */
@RestController
@RequestMapping("/api/exchange")
public class ExchangeRateController {

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyUsageRepository currencyUsageRepository;
    private final CurrencyCode currencyCode;
    private final SpreadCalculationService spreadCalculationService;
    private final UsageTrackingService usageTrackingService;
    private final RateIngestionService rateIngestionService;

    public ExchangeRateController(
            ExchangeRateRepository exchangeRateRepository,
            CurrencyUsageRepository currencyUsageRepository,
            CurrencyCode currencyCode,
            SpreadCalculationService spreadCalculationService,
            UsageTrackingService usageTrackingService,
            RateIngestionService rateIngestionService) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.currencyUsageRepository = currencyUsageRepository;
        this.currencyCode = currencyCode;
        this.spreadCalculationService = spreadCalculationService;
        this.usageTrackingService = usageTrackingService;
        this.rateIngestionService = rateIngestionService;
    }

    @GetMapping
    public ExchangeRateResponse getExchangeRate(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) LocalDate date) {
        String fromCode = from.toUpperCase();
        String toCode = to.toUpperCase();
        validateCurrency(fromCode);
        validateCurrency(toCode);

        BigDecimal adjustedRate;
        LocalDate rateDate;

        if (fromCode.equals(toCode)) {
            // Same-currency pair: always exactly 1, regardless of whether this
            // currency has any stored data at all (spec.md User Story 1,
            // Acceptance Scenario 4 — confirmed design decision). No DB lookup.
            adjustedRate = spreadCalculationService.calculate(
                    toCode, fromCode, BigDecimal.ONE, BigDecimal.ONE);
            rateDate = date != null ? date : LocalDate.now();
        } else {
            rateDate = date != null ? date : mostRecentCommonDate(fromCode, toCode);
            ExchangeRate fromRate = findRate(fromCode, toCode, rateDate, fromCode);
            ExchangeRate toRate = findRate(fromCode, toCode, rateDate, toCode);
            adjustedRate = spreadCalculationService.calculate(
                    toCode, fromCode, toRate.getRateToUsd(), fromRate.getRateToUsd());
        }

        // The usage increment must happen only after the rate has been
        // successfully resolved (contracts/exchange.md) — everything above this
        // line either returned via an exception or produced a real rate.
        usageTrackingService.recordLookup(fromCode, toCode, LocalDate.now());

        long fromQueryCount = currencyUsageRepository.findById(fromCode).orElseThrow().getQueryCount();
        long toQueryCount = currencyUsageRepository.findById(toCode).orElseThrow().getQueryCount();

        return new ExchangeRateResponse(fromCode, toCode, adjustedRate, rateDate, fromQueryCount, toQueryCount);
    }

    @GetMapping("/history")
    public HistoryResponse getHistory(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        String fromCode = from.toUpperCase();
        String toCode = to.toUpperCase();
        validateCurrency(fromCode);
        validateCurrency(toCode);
        if (startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException(
                    "startDate " + startDate + " is after endDate " + endDate);
        }

        List<HistoricalRatePoint> points = new ArrayList<>();
        List<LocalDate> missingDates = new ArrayList<>();

        if (fromCode.equals(toCode)) {
            // Same currency for every date in range: always exactly 1, no data
            // needed at all — consistent with getExchangeRate's same-currency
            // handling above.
            for (LocalDate cursor = startDate; !cursor.isAfter(endDate); cursor = cursor.plusDays(1)) {
                points.add(new HistoricalRatePoint(cursor, BigDecimal.ONE));
            }
        } else {
            Map<LocalDate, BigDecimal> fromRates = ratesByDate(fromCode, startDate, endDate);
            Map<LocalDate, BigDecimal> toRates = ratesByDate(toCode, startDate, endDate);

            for (LocalDate cursor = startDate; !cursor.isAfter(endDate); cursor = cursor.plusDays(1)) {
                BigDecimal fromRateToUsd = fromRates.get(cursor);
                BigDecimal toRateToUsd = toRates.get(cursor);
                if (fromRateToUsd != null && toRateToUsd != null) {
                    BigDecimal adjustedRate = spreadCalculationService.calculate(
                            toCode, fromCode, toRateToUsd, fromRateToUsd);
                    points.add(new HistoricalRatePoint(cursor, adjustedRate));
                } else {
                    missingDates.add(cursor);
                }
            }

            if (points.isEmpty()) {
                throw new RateNotAvailableException(
                        "No rate data available for " + fromCode + "/" + toCode + " between "
                                + startDate + " and " + endDate);
            }
        }

        return new HistoryResponse(fromCode, toCode, startDate, endDate, points, missingDates);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh() {
        try {
            rateIngestionService.ingestLatestRates();
        } catch (FixerClientException e) {
            throw new UpstreamFetchException(
                    "Manual refresh failed: " + e.getMessage(), e);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new RefreshResponse(Instant.now(), "COMPLETED"));
    }

    private void validateCurrency(String code) {
        if (!currencyCode.isSupported(code)) {
            throw new UnknownCurrencyException("Unknown currency code: " + code);
        }
    }

    /** The later of two currencies' single most-recent stored dates, taking the earlier of the two. */
    private LocalDate mostRecentCommonDate(String fromCode, String toCode) {
        LocalDate fromLatest = latestDateOrThrow(fromCode, toCode);
        LocalDate toLatest = latestDateOrThrow(toCode, fromCode);
        return fromLatest.isBefore(toLatest) ? fromLatest : toLatest;
    }

    private LocalDate latestDateOrThrow(String code, String pairedCode) {
        return exchangeRateRepository
                .findTopByCurrencyCodeOrderByRateDateDesc(code)
                .map(ExchangeRate::getRateDate)
                .orElseThrow(
                        () ->
                                new RateNotAvailableException(
                                        "No rate data available for " + code + "/" + pairedCode));
    }

    private ExchangeRate findRate(String fromCode, String toCode, LocalDate rateDate, String code) {
        return exchangeRateRepository
                .findByCurrencyCodeAndRateDate(code, rateDate)
                .orElseThrow(
                        () ->
                                new RateNotAvailableException(
                                        "No rate data available for " + fromCode + "/" + toCode + " on "
                                                + rateDate));
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
