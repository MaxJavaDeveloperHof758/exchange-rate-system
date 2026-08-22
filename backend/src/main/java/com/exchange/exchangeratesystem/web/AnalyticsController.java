package com.exchange.exchangeratesystem.web;

import java.util.List;

import com.exchange.exchangeratesystem.usage.CurrencyUsageRepository;
import com.exchange.exchangeratesystem.usage.dto.AnalyticsResponse;
import com.exchange.exchangeratesystem.usage.dto.CurrencyUsageEntry;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/analytics} — usage statistics across all currencies ever queried
 * (T029, User Story 3), per contracts/analytics.md. Read-only: never touches
 * {@code currency_usage} counters, so viewing analytics never itself counts as
 * a lookup.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final CurrencyUsageRepository currencyUsageRepository;

    public AnalyticsController(CurrencyUsageRepository currencyUsageRepository) {
        this.currencyUsageRepository = currencyUsageRepository;
    }

    @GetMapping
    public AnalyticsResponse getAnalytics() {
        List<CurrencyUsageEntry> topCurrencies = currencyUsageRepository
                .findAllByOrderByQueryCountDesc()
                .stream()
                .map(usage -> new CurrencyUsageEntry(
                        usage.getCurrencyCode(), usage.getQueryCount(), usage.getLastQueriedDate()))
                .toList();
        return new AnalyticsResponse(topCurrencies);
    }
}
