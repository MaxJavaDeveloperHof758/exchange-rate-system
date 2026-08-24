package com.exchange.exchangeratesystem.error;

/**
 * A requested date range is malformed at the semantic level (e.g.
 * {@code startDate} after {@code endDate}) — maps to
 * {@code 400 INVALID_DATE_RANGE}, per contracts/exchange.md's
 * {@code GET /api/exchange/history} error table. Distinct from
 * {@code MethodArgumentTypeMismatchException}'s {@code INVALID_DATE_FORMAT},
 * which is about a single date string failing to parse at all, not two valid
 * dates being in the wrong order.
 */
public class InvalidDateRangeException extends ApiException {

    public InvalidDateRangeException(String message) {
        super("INVALID_DATE_RANGE", message);
    }
}
