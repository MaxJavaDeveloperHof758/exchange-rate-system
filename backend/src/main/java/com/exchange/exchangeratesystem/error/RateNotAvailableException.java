package com.exchange.exchangeratesystem.error;

/**
 * No stored rate exists for the requested currency/date (or anywhere in a
 * requested range) — maps to {@code 404 RATE_NOT_AVAILABLE} (FR-009).
 */
public class RateNotAvailableException extends ApiException {

    public RateNotAvailableException(String message) {
        super("RATE_NOT_AVAILABLE", message);
    }
}
