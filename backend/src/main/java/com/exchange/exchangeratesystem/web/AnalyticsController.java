package com.exchange.exchangeratesystem.web;

import java.util.List;

import com.exchange.exchangeratesystem.usage.CurrencyUsageRepository;
import com.exchange.exchangeratesystem.usage.dto.AnalyticsResponse;
import com.exchange.exchangeratesystem.usage.dto.CurrencyUsageEntry;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(
        name = "Analytics",
        description = "Usage statistics across all currencies ever queried — "
                + "contracts/analytics.md.")
public class AnalyticsController {

    private final CurrencyUsageRepository currencyUsageRepository;

    public AnalyticsController(CurrencyUsageRepository currencyUsageRepository) {
        this.currencyUsageRepository = currencyUsageRepository;
    }

    @Operation(
            summary = "Get usage statistics for every currency ever queried",
            description = "Read-only — viewing analytics never itself counts as a query "
                    + "(FR-011's scope is /api/exchange lookups only). A currency never queried "
                    + "does not appear in the list at all; an empty list is returned (never 404) "
                    + "when no lookups have occurred yet.")
    @ApiResponse(
            responseCode = "200",
            description = "Usage statistics, sorted by totalCount descending.",
            content = @Content(schema = @Schema(implementation = AnalyticsResponse.class)))
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
