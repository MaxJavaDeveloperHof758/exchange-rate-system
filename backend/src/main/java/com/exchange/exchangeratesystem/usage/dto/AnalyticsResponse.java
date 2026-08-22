package com.exchange.exchangeratesystem.usage.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/analytics} success response, per contracts/analytics.md. An
 * empty {@code topCurrencies} list (no lookups have ever occurred) is a valid
 * {@code 200}, never a {@code 404}.
 */
public record AnalyticsResponse(
        @Schema(
                description = "Every currency ever queried, sorted by totalCount descending. "
                        + "Empty (never 404) if no lookups have occurred yet. A currency never "
                        + "queried does not appear at all.")
                List<CurrencyUsageEntry> topCurrencies) {
}
