package com.exchange.exchangeratesystem.config;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.exchange.exchangeratesystem.rate.ExchangeRateRepository;

/**
 * T031: pre-populates the brief's EUR/PLN worked-example rates (Section 6.2)
 * for today's date, "dev"-profile only, so quickstart.md's steps are runnable
 * without waiting on the 12:05 AM GMT scheduler (FR-001) or spending a real
 * Fixer.io call. Uses the same idempotent upsert the real ingestion path uses
 * (T010/research.md Decision 3), so restarting the app never duplicates rows
 * or fails on a rerun — unlike {@link com.exchange.exchangeratesystem.rate.RateIngestionService},
 * this deliberately uses the system clock for the rate date, since its whole
 * purpose is "today, whenever you happen to run this locally."
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final BigDecimal EUR_RATE_TO_USD = new BigDecimal("0.80");
    private static final BigDecimal PLN_RATE_TO_USD = new BigDecimal("3.70");

    private final ExchangeRateRepository exchangeRateRepository;

    public DevDataSeeder(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Override
    public void run(String... args) {
        LocalDate today = LocalDate.now();
        exchangeRateRepository.upsert("EUR", EUR_RATE_TO_USD, today);
        exchangeRateRepository.upsert("PLN", PLN_RATE_TO_USD, today);
    }
}
