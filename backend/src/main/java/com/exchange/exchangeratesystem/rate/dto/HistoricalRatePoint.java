package com.exchange.exchangeratesystem.rate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One raw stored rate on one date, per contracts/exchange.md's {@code
 * GET /api/exchange/history} response. Only dates where a spread-adjusted
 * rate could actually be computed appear as points — see {@link
 * HistoryResponse#missingDates()} for the rest.
 */
public record HistoricalRatePoint(
        @Schema(description = "The date this rate applies to.", example = "2024-03-01")
                LocalDate date,
        @Schema(description = "The spread-adjusted rate on this date.", example = "4.41")
                BigDecimal exchange) {
}
