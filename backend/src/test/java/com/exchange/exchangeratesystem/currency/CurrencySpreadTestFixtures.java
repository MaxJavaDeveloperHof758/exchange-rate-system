package com.exchange.exchangeratesystem.currency;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A {@link CurrencySpread} backed by the same real Appendix B values as
 * {@code application.yml}'s {@code currency.spread.*}, for tests that need
 * a real (not mocked) {@code CurrencySpread} without a Spring context.
 * {@link CurrencySpreadPropertiesBindingTest} is what actually proves the
 * real YAML file binds to these same values — this fixture exists purely
 * for convenience in unit tests that would otherwise need a full
 * {@code @SpringBootTest} just to get a working {@code CurrencySpread}.
 */
public final class CurrencySpreadTestFixtures {

    private CurrencySpreadTestFixtures() {
    }

    public static CurrencySpread realAppendixB() {
        return new CurrencySpread(
                new CurrencySpreadProperties(
                        new BigDecimal("2.75"),
                        Map.ofEntries(
                                Map.entry("EUR", new BigDecimal("0.00")),
                                Map.entry("JPY", new BigDecimal("3.25")),
                                Map.entry("HKD", new BigDecimal("3.25")),
                                Map.entry("KRW", new BigDecimal("3.25")),
                                Map.entry("MYR", new BigDecimal("4.50")),
                                Map.entry("INR", new BigDecimal("4.50")),
                                Map.entry("MXN", new BigDecimal("4.50")),
                                Map.entry("RUB", new BigDecimal("6.00")),
                                Map.entry("CNY", new BigDecimal("6.00")),
                                Map.entry("ZAR", new BigDecimal("6.00")))));
    }
}
