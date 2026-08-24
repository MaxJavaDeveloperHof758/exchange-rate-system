package com.exchange.exchangeratesystem.currency;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Fixed Appendix B spread lookup table. Not persisted per-fetch — this is a static
 * business rule, not observed provider data. See data-model.md's "Reference Table:
 * Currency Spread" and constitution Principle II.
 */
@Component
public class CurrencySpread {

    /**
     * Fixer.io's free-tier subscription (brief Section 2) is contractually fixed to
     * EUR as its base currency — it cannot be changed without a paid plan. Appendix
     * B's "Base Currency (as returned by the Fixer.io API key)" tier is therefore
     * EUR for this project's scope.
     */
    private static final String BASE_CURRENCY = "EUR";

    private static final BigDecimal BASE_CURRENCY_SPREAD = new BigDecimal("0.00");
    private static final BigDecimal JPY_HKD_KRW_SPREAD = new BigDecimal("3.25");
    private static final BigDecimal MYR_INR_MXN_SPREAD = new BigDecimal("4.50");
    private static final BigDecimal RUB_CNY_ZAR_SPREAD = new BigDecimal("6.00");
    private static final BigDecimal DEFAULT_SPREAD = new BigDecimal("2.75");

    private static final Set<String> JPY_HKD_KRW = Set.of("JPY", "HKD", "KRW");
    private static final Set<String> MYR_INR_MXN = Set.of("MYR", "INR", "MXN");
    private static final Set<String> RUB_CNY_ZAR = Set.of("RUB", "CNY", "ZAR");

    /**
     * Looks up the fixed spread percentage for a currency code per Appendix B.
     * Unrecognized codes fall through to the "all other currencies" default —
     * currency-code validity itself is CurrencyCode's responsibility (T008), not
     * this lookup's.
     */
    public BigDecimal spreadFor(String currencyCode) {
        String code = currencyCode.toUpperCase(Locale.ROOT);

        if (BASE_CURRENCY.equals(code)) {
            return BASE_CURRENCY_SPREAD;
        }
        if (JPY_HKD_KRW.contains(code)) {
            return JPY_HKD_KRW_SPREAD;
        }
        if (MYR_INR_MXN.contains(code)) {
            return MYR_INR_MXN_SPREAD;
        }
        if (RUB_CNY_ZAR.contains(code)) {
            return RUB_CNY_ZAR_SPREAD;
        }
        return DEFAULT_SPREAD;
    }
}
