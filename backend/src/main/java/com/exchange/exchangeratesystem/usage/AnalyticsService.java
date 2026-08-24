package com.exchange.exchangeratesystem.usage;

import java.util.List;

import org.springframework.stereotype.Service;

import com.exchange.exchangeratesystem.usage.dto.AnalyticsResponse;
import com.exchange.exchangeratesystem.usage.dto.CurrencyUsageEntry;

/**
 * The application-service half of {@code GET /api/analytics} (extracted
 * from {@code AnalyticsController}, which previously read the repository
 * directly): usage statistics across every currency ever queried, sorted
 * by count descending, per contracts/analytics.md. Read-only — never
 * touches {@code currency_usage} counters, so viewing analytics never
 * itself counts as a query (FR-011's scope is {@code /api/exchange}
 * lookups only).
 */
@Service
public class AnalyticsService {

    private final CurrencyUsageRepository currencyUsageRepository;

    public AnalyticsService(CurrencyUsageRepository currencyUsageRepository) {
        this.currencyUsageRepository = currencyUsageRepository;
    }

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
