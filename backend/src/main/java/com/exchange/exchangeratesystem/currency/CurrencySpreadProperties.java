package com.exchange.exchangeratesystem.currency;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds Appendix B's spread lookup table from {@code application.yml}'s
 * {@code currency.spread.*} — externalized so adding, changing, or
 * re-tiering a currency's spread is a config edit, not a code change/
 * redeploy (previously hardcoded directly in {@link CurrencySpread}).
 *
 * {@code tiers} maps a currency code to its spread percentage; a code not
 * present in the map falls through to {@code defaultSpread} ("all other
 * currencies," Appendix B). {@code application.yml} quotes every value as
 * a YAML string ({@code "2.75"}) rather than a bare numeric scalar — a
 * defensive habit for anything feeding a {@code BigDecimal}
 * (constitution Principle I), even though
 * {@code CurrencySpreadPropertiesBindingTest} confirmed this Spring Boot
 * version's YAML loader binds either form with identical precision; the
 * quoting just avoids depending on that continuing to hold.
 */
@ConfigurationProperties(prefix = "currency.spread")
public record CurrencySpreadProperties(BigDecimal defaultSpread, Map<String, BigDecimal> tiers) {
}
