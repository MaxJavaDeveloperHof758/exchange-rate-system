package com.exchange.exchangeratesystem.rate;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fetches the latest rates from Fixer.io and upserts every returned currency
 * for that one validity date, per research.md Decision 3 and NFR-004.
 *
 * The Fixer.io call runs with no transaction open at all — not even one
 * whose connection is merely acquired lazily. Only the upsert batch
 * (persistRates) opens one, via {@code TransactionTemplate} rather than a
 * same-class {@code @Transactional} method: Spring's proxy-based AOP does
 * not intercept a method calling another method on {@code this} within the
 * same instance, so a same-class {@code @Transactional} split here would
 * compile but silently run {@code persistRates} with no transaction at all
 * — defeating the "every upsert in this run rolls back together" guarantee
 * entirely, and doing so without any error to reveal it. See
 * {@code UsageTrackingService} for the same reasoning already applied there.
 *
 * The whole batch of upserts for one ingestion run still rolls back
 * together if any one upsert fails partway through — this run never leaves
 * a partial set of currencies updated for a date while others are missing.
 *
 * If the Fixer.io fetch itself fails, no repository call has happened yet,
 * so all existing {@code ExchangeRate} rows are left untouched by
 * construction — the failure is logged here (in addition to FixerClient's
 * own lower-level log) and re-thrown so callers (the scheduler, T017; the
 * manual-refresh endpoint, T028) know the run did not succeed.
 */
@Service
public class RateIngestionService {

    private static final Logger log = LoggerFactory.getLogger(RateIngestionService.class);

    private final FixerClient fixerClient;
    private final ExchangeRateRepository exchangeRateRepository;
    private final TransactionTemplate transactionTemplate;

    public RateIngestionService(
            FixerClient fixerClient,
            ExchangeRateRepository exchangeRateRepository,
            PlatformTransactionManager transactionManager) {
        this.fixerClient = fixerClient;
        this.exchangeRateRepository = exchangeRateRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

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

        transactionTemplate.executeWithoutResult(status -> persistRates(result));

        log.info(
                "Rate ingestion succeeded: upserted {} currencies for {}",
                result.rates().size(),
                result.date());
    }

    private void persistRates(FixerRatesResult result) {
        for (Map.Entry<String, BigDecimal> entry : result.rates().entrySet()) {
            exchangeRateRepository.upsert(entry.getKey(), entry.getValue(), result.date());
        }
    }
}
