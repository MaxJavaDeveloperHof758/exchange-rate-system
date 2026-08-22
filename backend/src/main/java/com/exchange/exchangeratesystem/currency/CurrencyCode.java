package com.exchange.exchangeratesystem.currency;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * The fixed set of ISO 4217 currency codes this system recognizes. Used by
 * controllers to reject unrecognized currency codes with 400 (FR-010).
 *
 * Not the full ~180-entry ISO 4217 list — a curated set of major, commonly
 * traded currencies, guaranteed to include every currency named in the brief
 * (Appendix B's spread groups, and the EUR/PLN worked example).
 */
@Component
public class CurrencyCode {

    private static final Set<String> SUPPORTED_CODES = Set.of(
            // Appendix B — base currency
            "EUR",
            // Appendix B — JPY/HKD/KRW group
            "JPY", "HKD", "KRW",
            // Appendix B — MYR/INR/MXN group
            "MYR", "INR", "MXN",
            // Appendix B — RUB/CNY/ZAR group
            "RUB", "CNY", "ZAR",
            // Worked example (Section 6.2)
            "PLN",
            // Other major/commonly-traded currencies
            "USD", "GBP", "CHF", "CAD", "AUD", "NZD", "SGD", "BRL",
            "SEK", "NOK", "DKK", "CZK", "HUF", "TRY", "ILS", "AED",
            "SAR", "THB", "IDR", "PHP", "VND", "EGP", "NGN", "KES",
            "PKR", "BDT", "TWD", "CLP", "COP", "ARS", "PEN");

    /**
     * @return true if {@code code} (case-insensitive) is a recognized currency;
     *         false for null, blank, or unrecognized input.
     */
    public boolean isSupported(String code) {
        if (code == null) {
            return false;
        }
        return SUPPORTED_CODES.contains(code.toUpperCase());
    }
}
