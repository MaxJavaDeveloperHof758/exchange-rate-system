package com.exchange.exchangeratesystem.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Confirms currency-code matching is safe under the Turkish locale's
 * well-known {@code toUpperCase()} pitfall (lowercase "i" uppercases to
 * "İ" - U+0130 - not plain ASCII "I"). IDR (Indonesian Rupiah) is the
 * concrete, real member of {@link CurrencyCode}'s supported set a
 * locale-unsafe {@code toUpperCase()} would silently break: lowercase
 * "idr" would uppercase to "İDR" under Turkish, which never matches the
 * plain ASCII "IDR" this class actually stores.
 */
class CurrencyCodeTest {

    private final CurrencyCode currencyCode = new CurrencyCode();
    private Locale originalDefault;

    @BeforeEach
    void forceTurkishDefaultLocale() {
        originalDefault = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
    }

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(originalDefault);
    }

    @Test
    void recognizesLowercaseIdrEvenUnderTurkishDefaultLocale() {
        // Confirms the premise itself under this test's forced Turkish
        // default - otherwise this test would pass even with the
        // Locale.ROOT fix reverted, silently proving nothing.
        assertThat("idr".toUpperCase()).isNotEqualTo("IDR");

        assertThat(currencyCode.isSupported("idr")).isTrue();
    }

    @Test
    void stillRejectsUnknownCodesUnderTurkishDefaultLocale() {
        assertThat(currencyCode.isSupported("zzz")).isFalse();
    }
}
