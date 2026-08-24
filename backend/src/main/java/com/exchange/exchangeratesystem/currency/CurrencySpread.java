package com.exchange.exchangeratesystem.currency;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Appendix B spread lookup, backed by {@link CurrencySpreadProperties}
 * (bound from {@code application.yml}'s {@code currency.spread.*}) rather
 * than a hardcoded table — not persisted per-fetch either way, since this
 * is a static business rule, not observed provider data. See
 * data-model.md's "Reference Table: Currency Spread" and constitution
 * Principle II.
 */
@Component
public class CurrencySpread {

    private final CurrencySpreadProperties properties;

    public CurrencySpread(CurrencySpreadProperties properties) {
        this.properties = properties;
    }

    /**
     * Looks up the fixed spread percentage for a currency code per Appendix B.
     * Unrecognized codes fall through to the "all other currencies" default —
     * currency-code validity itself is CurrencyCode's responsibility, not
     * this lookup's.
     */
    public BigDecimal spreadFor(String currencyCode) {
        String code = currencyCode.toUpperCase(Locale.ROOT);
        return properties.tiers().getOrDefault(code, properties.defaultSpread());
    }
}
