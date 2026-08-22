package com.exchange.exchangeratesystem.usage.dto;

import java.util.List;

/**
 * {@code GET /api/analytics} success response, per contracts/analytics.md. An
 * empty {@code topCurrencies} list (no lookups have ever occurred) is a valid
 * {@code 200}, never a {@code 404}.
 */
public record AnalyticsResponse(List<CurrencyUsageEntry> topCurrencies) {
}
