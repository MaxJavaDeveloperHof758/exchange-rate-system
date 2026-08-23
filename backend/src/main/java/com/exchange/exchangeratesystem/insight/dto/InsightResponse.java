package com.exchange.exchangeratesystem.insight.dto;

import java.time.LocalDate;

/** {@code GET /api/exchange/insight} success response, per contracts/insight.md. */
public record InsightResponse(
        String from,
        String to,
        LocalDate fromDate,
        LocalDate toDate,
        String insight) {
}
