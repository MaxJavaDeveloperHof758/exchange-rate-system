package com.exchange.exchangeratesystem.rate.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/exchange/history} success response, per contracts/exchange.md.
 * {@code missingDates} explicitly lists any date within
 * {@code [startDate, endDate]} that had no usable stored data for one or both
 * currencies (spec.md User Story 2, Acceptance Scenario 5) — a partially
 * incomplete range is still a {@code 200}, never a hard failure.
 */
public record HistoryResponse(
        String from,
        String to,
        LocalDate startDate,
        LocalDate endDate,
        List<HistoricalRatePoint> points,
        List<LocalDate> missingDates) {
}
