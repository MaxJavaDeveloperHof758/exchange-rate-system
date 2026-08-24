package com.exchange.exchangeratesystem.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.exchange.exchangeratesystem.support.PostgresTestContainerConfig;

/**
 * Confirms {@code application.yml}'s {@code currency.spread.*} actually
 * binds into {@link CurrencySpreadProperties} with exact {@code BigDecimal}
 * precision — boots the real Spring context (not a fabricated property
 * source), so this exercises the actual shipped YAML file, not a fixture's
 * assumption about how it binds. Constitution Principle I is explicit that
 * no intermediate in the rate-calculation path may be {@code double}/
 * {@code float}; {@code isEqualByComparingTo} would catch even a tiny
 * binary-floating-point artifact a double-routed YAML scalar could
 * introduce (e.g. {@code 2.7500000000000004}), not just a scale mismatch.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "fixer.api-key=test-key")
@Import(PostgresTestContainerConfig.class)
class CurrencySpreadPropertiesBindingTest {

    @Autowired
    private CurrencySpreadProperties properties;

    @Test
    void bindsEveryAppendixBTierAndTheDefaultWithExactPrecision() {
        assertThat(properties.tiers().get("EUR")).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(properties.tiers().get("JPY")).isEqualByComparingTo(new BigDecimal("3.25"));
        assertThat(properties.tiers().get("HKD")).isEqualByComparingTo(new BigDecimal("3.25"));
        assertThat(properties.tiers().get("KRW")).isEqualByComparingTo(new BigDecimal("3.25"));
        assertThat(properties.tiers().get("MYR")).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(properties.tiers().get("INR")).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(properties.tiers().get("MXN")).isEqualByComparingTo(new BigDecimal("4.50"));
        assertThat(properties.tiers().get("RUB")).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(properties.tiers().get("CNY")).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(properties.tiers().get("ZAR")).isEqualByComparingTo(new BigDecimal("6.00"));
        assertThat(properties.defaultSpread()).isEqualByComparingTo(new BigDecimal("2.75"));
    }
}
