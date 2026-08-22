package com.exchange.exchangeratesystem.rate;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fetches the latest rates from Fixer.io and upserts every returned currency
 * for that one validity date, per research.md Decision 3 and NFR-004.
 *
 * The whole batch of upserts for one ingestion run is wrapped in a single
 * transaction: if any one upsert fails partway through, every upsert in this
 * run rolls back together — this run never leaves a partial set of currencies
 * updated for a date while others are missing. The Fixer.io call itself
 * happens inside this same {@code @Transactional} method, but before any
 * repository call — Spring's transaction manager acquires the database
 * connection lazily on first actual use, so no connection is held open for the
 * duration of the outbound HTTP call.
 *
 * If the Fixer.io fetch itself fails, no repository call has happened yet, so
 * all existing {@code ExchangeRate} rows are left untouched by construction —
 * the failure is logged here (in addition to FixerClient's own lower-level
 * log) and re-thrown so callers (the scheduler, T017; the manual-refresh
 * endpoint, T028) know the run did not succeed.
 */
@Service
public class RateIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RateIngestionService.class);

    private final FixerClient fixerClient;
    private final ExchangeRateRepository exchangeRateRepository;

    public RateIngestionService(
            FixerClient fixerClient, ExchangeRateRepository exchangeRateRepository) {
        this.fixerClient = fixerClient;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Transactional
    public void ingestLatestRates() {
        FixerRatesResult result;
        try {
            result = fixerClient.fetchLatestRates();
        } catch (FixerClientException e) {
            log.error(
                    "Rate ingestion aborted — Fixer.io fetch failed; "
                            + "existing exchange_rate data left untouched: {}",
                    e.getMessage());
            throw e;
        }

        for (Map.Entry<String, BigDecimal> entry : result.rates().entrySet()) {
            exchangeRateRepository.upsert(entry.getKey(), entry.getValue(), result.date());
        }

        log.info(
                "Rate ingestion succeeded: upserted {} currencies for {}",
                result.rates().size(),
                result.date());
    }
}
