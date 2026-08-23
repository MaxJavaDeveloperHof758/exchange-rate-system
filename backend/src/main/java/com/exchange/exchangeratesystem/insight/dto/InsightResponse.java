package com.exchange.exchangeratesystem.insight.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code GET /api/exchange/insight} success response, per contracts/insight.md. */
public record InsightResponse(
        @Schema(description = "Source currency code.", example = "EUR") String from,
        @Schema(description = "Target currency code.", example = "GBP") String to,
        @Schema(description = "Inclusive start of the summarized date range.", example = "2024-02-01")
                LocalDate fromDate,
        @Schema(description = "Inclusive end of the summarized date range.", example = "2024-03-01")
                LocalDate toDate,
        @Schema(
                description = "The AI-generated commentary, grounded in the actual stored rate "
                        + "series for this exact pair and range.",
                example = "EUR/GBP softened by approximately 1.8% over this period, with the "
                        + "steepest decline in the final week of February.")
                String insight) {
}
