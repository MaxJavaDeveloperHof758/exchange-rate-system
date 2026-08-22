package com.exchange.exchangeratesystem.rate.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code GET /api/exchange/history} success response, per contracts/exchange.md.
 * {@code missingDates} explicitly lists any date within
 * {@code [startDate, endDate]} that had no usable stored data for one or both
 * currencies (spec.md User Story 2, Acceptance Scenario 5) — a partially
 * incomplete range is still a {@code 200}, never a hard failure.
 */
public record HistoryResponse(
        @Schema(description = "Source currency code.", example = "EUR") String from,
        @Schema(description = "Target currency code.", example = "PLN") String to,
        @Schema(description = "Inclusive start of the requested range.", example = "2024-03-01")
                LocalDate startDate,
        @Schema(description = "Inclusive end of the requested range.", example = "2024-03-15")
                LocalDate endDate,
        @Schema(description = "One entry per date a rate could be computed for.")
                List<HistoricalRatePoint> points,
        @Schema(
                description = "Dates within the range with no usable stored data for one or "
                        + "both currencies.",
                example = "[\"2024-03-03\"]")
                List<LocalDate> missingDates) {
}
