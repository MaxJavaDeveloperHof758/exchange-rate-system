package com.exchange.exchangeratesystem.error;

/**
 * A requested currency code is not one of the system's recognized ISO 4217
 * codes (CurrencyCode, T008) — maps to {@code 400 UNKNOWN_CURRENCY} (FR-010).
 */
public class UnknownCurrencyException extends ApiException {

    public UnknownCurrencyException(String message) {
        super("UNKNOWN_CURRENCY", message);
    }
}
