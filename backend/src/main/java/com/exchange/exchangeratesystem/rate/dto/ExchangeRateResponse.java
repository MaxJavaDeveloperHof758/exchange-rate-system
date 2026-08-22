package com.exchange.exchangeratesystem.rate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code GET /api/exchange} success response, per contracts/exchange.md
 * (matches brief Appendix A exactly). {@code fromQueryCount}/{@code
 * toQueryCount} are the post-increment running totals for each currency,
 * reflecting the increment this successful call itself just performed
 * (FR-011).
 */
public record ExchangeRateResponse(
        String from,
        String to,
        BigDecimal exchange,
        LocalDate date,
        Long fromQueryCount,
        Long toQueryCount) {
}
