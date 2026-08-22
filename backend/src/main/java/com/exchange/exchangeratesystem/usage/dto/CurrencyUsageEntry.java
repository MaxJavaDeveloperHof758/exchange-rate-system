package com.exchange.exchangeratesystem.usage.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** One row of {@code GET /api/analytics}'s {@code topCurrencies} list, per contracts/analytics.md. */
public record CurrencyUsageEntry(
        @Schema(description = "The currency this row's counts are for.", example = "EUR")
                String currency,
        @Schema(
                description = "Total number of successful /api/exchange lookups this currency "
                        + "has participated in, on either side of the pair.",
                example = "142")
                long totalCount,
        @Schema(
                description = "The most recent date this currency was involved in a successful "
                        + "lookup.",
                example = "2024-03-15")
                LocalDate lastQueried) {
}
