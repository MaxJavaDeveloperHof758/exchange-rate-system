package com.exchange.exchangeratesystem.rate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One point on one date, per contracts/exchange.md's {@code GET
 * /api/exchange/history} response. Only dates where a spread-adjusted rate
 * could actually be computed appear as points — see {@link
 * HistoryResponse#missingDates()} for the rest.
 *
 * Carries each currency's own raw stored rate-to-USD alongside the derived
 * pair rate — FR-014 requires "the raw rates that are actually stored," and
 * the underlying stored entity (data-model.md's Exchange Rate Record) is
 * per-currency, not per-pair; returning only {@code adjustedRate} (as this
 * originally did, under the name {@code exchange}) exposed the computed
 * value but never the raw data FR-014 actually asks for. {@code
 * fromRateToUsd}/{@code toRateToUsd} are {@code null} for a same-currency
 * pair's points: that case is a constant {@code adjustedRate} of {@code 1}
 * with deliberately no database lookup at all (consistent with
 * {@code ExchangeRateQueryService}'s same-currency handling), so there is
 * no raw rate to report.
 */
public record HistoricalRatePoint(
        @Schema(description = "The date this rate applies to.", example = "2024-03-01")
                LocalDate date,
        @Schema(
                description = "The source currency's own raw rate-to-USD as actually stored for "
                        + "this date. Null for a same-currency pair (no lookup is performed).",
                example = "0.80")
                BigDecimal fromRateToUsd,
        @Schema(
                description = "The target currency's own raw rate-to-USD as actually stored for "
                        + "this date. Null for a same-currency pair (no lookup is performed).",
                example = "3.70")
                BigDecimal toRateToUsd,
        @Schema(description = "The spread-adjusted pair rate on this date.", example = "4.41")
                BigDecimal adjustedRate) {
}
