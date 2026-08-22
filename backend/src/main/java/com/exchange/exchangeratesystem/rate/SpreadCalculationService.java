package com.exchange.exchangeratesystem.rate;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.exchange.exchangeratesystem.currency.CurrencySpread;

/**
 * The spread-adjusted rate formula, exactly per brief Section 6.1 / constitution
 * Principle II: {@code (toRate/fromRate) × ((100 − MAX(toSpread, fromSpread))/100)}.
 * Every division uses {@code BigDecimal.divide(divisor, scale, RoundingMode.HALF_UP)} —
 * never the zero-argument overload, and no {@code double}/{@code float} anywhere
 * (Principle I).
 */
@Service
public class SpreadCalculationService {

    private static final int SCALE = 10;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CurrencySpread currencySpread;

    public SpreadCalculationService(CurrencySpread currencySpread) {
        this.currencySpread = currencySpread;
    }

    /** The pure formula — no currency-code knowledge, no lookups. */
    public BigDecimal calculate(
            BigDecimal toRateToUsd,
            BigDecimal fromRateToUsd,
            BigDecimal toSpread,
            BigDecimal fromSpread) {
        BigDecimal rateRatio = toRateToUsd.divide(fromRateToUsd, SCALE, RoundingMode.HALF_UP);
        BigDecimal maxSpread = toSpread.max(fromSpread);
        BigDecimal spreadFactor =
                ONE_HUNDRED.subtract(maxSpread).divide(ONE_HUNDRED, SCALE, RoundingMode.HALF_UP);
        return rateRatio.multiply(spreadFactor);
    }

    /**
     * Convenience overload: looks up each currency's fixed Appendix B spread via
     * {@link CurrencySpread} (T007) before delegating to the pure formula.
     *
     * A currency converted to itself is always exactly 1, with no spread applied
     * (spec.md User Story 1, Acceptance Scenario 4) — this is enforced here as an
     * explicit short-circuit, not left to arise naturally from the formula, since
     * the formula alone would still apply a (semantically meaningless) spread to
     * a same-currency pair whenever that currency's spread is non-zero.
     */
    public BigDecimal calculate(
            String toCurrencyCode,
            String fromCurrencyCode,
            BigDecimal toRateToUsd,
            BigDecimal fromRateToUsd) {
        if (toCurrencyCode.equalsIgnoreCase(fromCurrencyCode)) {
            return BigDecimal.ONE;
        }
        BigDecimal toSpread = currencySpread.spreadFor(toCurrencyCode);
        BigDecimal fromSpread = currencySpread.spreadFor(fromCurrencyCode);
        return calculate(toRateToUsd, fromRateToUsd, toSpread, fromSpread);
    }
}
