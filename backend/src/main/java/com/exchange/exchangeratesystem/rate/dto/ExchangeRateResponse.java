package com.exchange.exchangeratesystem.rate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/exchange} success response, per contracts/exchange.md
 * (matches brief Appendix A exactly). {@code fromQueryCount}/{@code
 * toQueryCount} are the post-increment running totals for each currency,
 * reflecting the increment this successful call itself just performed
 * (FR-011).
 */
public record ExchangeRateResponse(
        @Schema(description = "Source currency code.", example = "EUR") String from,
        @Schema(description = "Target currency code.", example = "PLN") String to,
        @Schema(
                description = "The spread-adjusted rate: 1 unit of `from` converts to this many "
                        + "units of `to`. Always exactly 1 for a same-currency pair.",
                example = "4.4405487565413254")
                BigDecimal exchange,
        @Schema(
                description = "The rate date actually used — either the requested `date` or, if "
                        + "omitted, the most recent date with stored data for both currencies.",
                example = "2024-03-15")
                LocalDate date,
        @Schema(
                description = "`from`'s running usage count, including this lookup.",
                example = "142")
                Long fromQueryCount,
        @Schema(
                description = "`to`'s running usage count, including this lookup.",
                example = "37")
                Long toQueryCount) {
}
