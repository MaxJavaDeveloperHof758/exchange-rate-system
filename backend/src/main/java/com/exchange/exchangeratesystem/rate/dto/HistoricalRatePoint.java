package com.exchange.exchangeratesystem.rate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One raw stored rate on one date, per contracts/exchange.md's {@code
 * GET /api/exchange/history} response. Only dates where a spread-adjusted
 * rate could actually be computed appear as points — see {@link
 * HistoryResponse#missingDates()} for the rest.
 */
public record HistoricalRatePoint(LocalDate date, BigDecimal exchange) {
}
