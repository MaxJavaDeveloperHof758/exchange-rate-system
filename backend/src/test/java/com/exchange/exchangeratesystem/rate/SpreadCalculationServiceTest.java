package com.exchange.exchangeratesystem.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;

import com.exchange.exchangeratesystem.currency.CurrencySpreadTestFixtures;

class SpreadCalculationServiceTest {

    private final SpreadCalculationService service =
            new SpreadCalculationService(CurrencySpreadTestFixtures.realAppendixB());

    /**
     * Every Appendix B tier's numeric spread value, isolated: fromSpread pinned
     * at 0 so MAX always resolves to the tier's own spread, with toRate ==
     * fromRate == 1 so the rate-ratio term is always exactly 1 and the result is
     * just the spread factor itself — proving the formula/MAX logic per tier,
     * independent of where the spread value came from.
     */
    @ParameterizedTest(name = "tier spread {0}% -> factor {1}")
    @CsvSource({
        "0.00, 1.0000000000", // base currency
        "3.25, 0.9675000000", // JPY/HKD/KRW
        "4.50, 0.9550000000", // MYR/INR/MXN
        "6.00, 0.9400000000", // RUB/CNY/ZAR
        "2.75, 0.9725000000" // all other currencies
    })
    void everyAppendixBTierSpreadIsAppliedCorrectly(String tierSpread, String expectedFactor) {
        BigDecimal result =
                service.calculate(
                        BigDecimal.ONE, BigDecimal.ONE, new BigDecimal(tierSpread), BigDecimal.ZERO);

        assertThat(result).isEqualByComparingTo(new BigDecimal(expectedFactor));
    }

    /**
     * The brief's own worked example (Section 6.2). These spreads (1% / 4%) are
     * illustrative constants for demonstrating the formula, not the real
     * Appendix B tier values for EUR/PLN — deliberately NOT routed through
     * CurrencySpread, per this exact instruction.
     */
    @Test
    void eurPlnWorkedExampleYieldsExactly4_44() {
        BigDecimal result =
                service.calculate(
                        new BigDecimal("3.7"),
                        new BigDecimal("0.8"),
                        new BigDecimal("4"),
                        new BigDecimal("1"));

        assertThat(result).isEqualByComparingTo(new BigDecimal("4.44"));
    }

    @Test
    void sameCurrencyOnBothSidesYieldsExactlyOneWithNoSpreadApplied() {
        // Even with a non-zero rate and the currency's real (non-zero) spread,
        // a currency converted to itself must be exactly 1 — the formula alone
        // would not produce this if a real spread were applied (see the tier
        // test above: any non-zero spread yields a factor below 1).
        BigDecimal result =
                service.calculate("JPY", "JPY", new BigDecimal("110.5"), new BigDecimal("110.5"));

        assertThat(result).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void tiedSpreadsUseEitherSideSinceMaxOfEqualValuesIsThatValue() {
        BigDecimal result =
                service.calculate(
                        new BigDecimal("2"),
                        new BigDecimal("1"),
                        new BigDecimal("3.25"),
                        new BigDecimal("3.25"));

        // ratio = 2/1 = 2; factor = (100-3.25)/100 = 0.9675; 2 * 0.9675 = 1.935
        assertThat(result).isEqualByComparingTo(new BigDecimal("1.935"));
    }

    /**
     * "Missing" currency, in the sense relevant to this service: CurrencySpread
     * (T007) never throws for an unrecognized code — it falls through to the
     * 2.75% default by design, so there is no "not found" case at that layer.
     * The only way this overload can fail is a null code, which it lets fail
     * fast rather than silently computing a wrong result — currency-code
     * *validity* is CurrencyCode's (T008) responsibility, enforced upstream by
     * controllers before this service is ever reached.
     */
    @Test
    void nullCurrencyCodeFailsFastRatherThanSilentlyMiscalculating() {
        assertThatThrownBy(
                        () ->
                                service.calculate(
                                        null, "PLN", new BigDecimal("3.7"), new BigDecimal("0.8")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void unrecognizedCurrencyCodeFallsThroughToTheDefaultSpreadRatherThanThrowing() {
        // Not "missing" in the sense of an error — CurrencySpread's designed
        // fallback for any code outside the four named tiers.
        BigDecimal result =
                service.calculate("ZZZ", "EUR", new BigDecimal("1"), new BigDecimal("1"));

        // EUR (base, 0%) vs ZZZ (falls through to 2.75%) -> MAX = 2.75%
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.9725000000"));
    }
}
