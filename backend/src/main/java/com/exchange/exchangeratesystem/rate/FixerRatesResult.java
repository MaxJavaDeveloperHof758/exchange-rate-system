package com.exchange.exchangeratesystem.rate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * The plain result of a Fixer.io {@code /latest} fetch: the validity date as
 * reported by the provider (never the fetch time, FR-002) and the rate for each
 * returned currency, relative to the provider's base currency.
 */
public record FixerRatesResult(LocalDate date, Map<String, BigDecimal> rates) {
}
