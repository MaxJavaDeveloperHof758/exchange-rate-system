package com.exchange.exchangeratesystem.usage.dto;

import java.time.LocalDate;

/** One row of {@code GET /api/analytics}'s {@code topCurrencies} list, per contracts/analytics.md. */
public record CurrencyUsageEntry(
        String currency,
        long totalCount,
        LocalDate lastQueried) {
}
