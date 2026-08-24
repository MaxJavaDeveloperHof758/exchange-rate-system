package com.exchange.exchangeratesystem.config;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.exchange.exchangeratesystem.rate.ExchangeRateRepository;

/**
 * Pre-populates the brief's EUR/PLN worked-example rates (Section 6.2)
 * for the last {@value #SEED_DAYS} days, "dev"-profile only, so
 * quickstart.md's steps are runnable without waiting on the 12:05 AM GMT
 * scheduler (FR-001) or spending a real Fixer.io call, and so the Historical
 * Trend view has more than a single day to plot on first run. Uses the same
 * idempotent upsert the real ingestion path uses, so restarting the app
 * never duplicates rows or fails on a rerun —
 * unlike {@link com.exchange.exchangeratesystem.rate.RateIngestionService},
 * this deliberately uses the system clock for the rate dates, since its
 * whole purpose is "recent days, whenever you happen to run this locally."
 *
 * Today's own row is always exactly EUR 0.80 / PLN 3.70 (the worked
 * example's own values, unchanged from the original single-day seed) so the
 * already-documented quickstart.md expected value keeps holding; only the
 * preceding days vary, by a small fixed daily step, purely to give the
 * trend chart a real (if synthetic) week of movement to draw.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final int SEED_DAYS = 7;

    private static final BigDecimal EUR_RATE_TO_USD = new BigDecimal("0.80");
    private static final BigDecimal PLN_RATE_TO_USD = new BigDecimal("3.70");
    private static final BigDecimal PLN_DAILY_STEP = new BigDecimal("0.01");

    private final ExchangeRateRepository exchangeRateRepository;

    public DevDataSeeder(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Override
    public void run(String... args) {
        LocalDate today = LocalDate.now();
        for (int daysAgo = 0; daysAgo < SEED_DAYS; daysAgo++) {
            LocalDate date = today.minusDays(daysAgo);
            BigDecimal plnRate = PLN_RATE_TO_USD.subtract(PLN_DAILY_STEP.multiply(BigDecimal.valueOf(daysAgo)));
            exchangeRateRepository.upsert("EUR", EUR_RATE_TO_USD, date);
            exchangeRateRepository.upsert("PLN", plnRate, date);
        }
    }
}
