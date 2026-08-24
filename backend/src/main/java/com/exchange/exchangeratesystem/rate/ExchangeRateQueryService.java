package com.exchange.exchangeratesystem.rate;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.exchange.exchangeratesystem.error.RateNotAvailableException;
import com.exchange.exchangeratesystem.rate.dto.ExchangeRateResponse;
import com.exchange.exchangeratesystem.usage.CurrencyUsageRepository;
import com.exchange.exchangeratesystem.usage.UsageTrackingService;

/**
 * The application-service half of {@code GET /api/exchange} (extracted from
 * {@code ExchangeRateController}, which previously did this directly): date
 * resolution, the spread-adjusted rate lookup itself, the same-currency
 * short-circuit rule, recording the lookup's usage counters on success, and
 * assembling the full response — everything the controller doesn't need to
 * know about beyond "validate the input, call this, return what it gives
 * back."
 */
@Service
public class ExchangeRateQueryService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyUsageRepository currencyUsageRepository;
    private final SpreadCalculationService spreadCalculationService;
    private final UsageTrackingService usageTrackingService;

    public ExchangeRateQueryService(
            ExchangeRateRepository exchangeRateRepository,
            CurrencyUsageRepository currencyUsageRepository,
            SpreadCalculationService spreadCalculationService,
            UsageTrackingService usageTrackingService) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.currencyUsageRepository = currencyUsageRepository;
        this.spreadCalculationService = spreadCalculationService;
        this.usageTrackingService = usageTrackingService;
    }

    /**
     * Resolves the spread-adjusted rate for {@code fromCode}/{@code toCode}
     * on {@code date} (or, if {@code null}, the most recent date both
     * currencies actually have stored data for), records the lookup's usage
     * counters, and returns the full response including each currency's
     * post-increment count. A same-currency pair always returns exactly
     * {@code 1} with no spread applied and no DB lookup for the rate itself
     * (spec.md User Story 1, Acceptance Scenario 4), but still records a
     * usage lookup for it.
     */
    public ExchangeRateResponse getRate(String fromCode, String toCode, LocalDate date) {
        BigDecimal adjustedRate;
        LocalDate rateDate;

        if (fromCode.equals(toCode)) {
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
        // successfully resolved (contracts/exchange.md) — everything above
        // this line either returned via an exception or produced a real rate.
        usageTrackingService.recordLookup(fromCode, toCode, LocalDate.now());

        long fromQueryCount = currencyUsageRepository.findById(fromCode).orElseThrow().getQueryCount();
        long toQueryCount = currencyUsageRepository.findById(toCode).orElseThrow().getQueryCount();

        return new ExchangeRateResponse(fromCode, toCode, adjustedRate, rateDate, fromQueryCount, toQueryCount);
    }

    /**
     * The most recent date on which both currencies actually have stored
     * data — resolved as one query (findMostRecentCommonDate), not by
     * comparing each currency's own single latest date against the other's.
     * Comparing single latest dates can pick a date that only one of the
     * two currencies has (e.g. EUR's latest is the 10th, PLN's latest is
     * the 9th, but EUR has no data at all on the 9th) even when an actual
     * earlier common date does exist.
     */
    private LocalDate mostRecentCommonDate(String fromCode, String toCode) {
        return exchangeRateRepository
                .findMostRecentCommonDate(fromCode, toCode)
                .orElseThrow(
                        () ->
                                new RateNotAvailableException(
                                        "No common rate date available for " + fromCode + "/" + toCode));
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
}
