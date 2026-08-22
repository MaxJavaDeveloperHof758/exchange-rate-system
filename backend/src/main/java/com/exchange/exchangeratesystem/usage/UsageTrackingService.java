package com.exchange.exchangeratesystem.usage;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records a successful lookup against one or both currencies of a pair
 * (research.md Decision 5 / constitution Principle IV), per T024.
 *
 * {@link CurrencyUsageRepository#incrementUsage} is a plain UPDATE — it never
 * throws for a currency with no row yet, it just reports 0 rows affected. This
 * service is what turns that into a full upsert: on 0 rows affected, it tries
 * {@link CurrencyUsageRepository#insertNewRow} to create that currency's
 * first-ever row (with query_count already 1 — this lookup itself, not a
 * count-0 placeholder). If another thread wins a simultaneous race to create
 * the *same* brand-new currency's row, that insert fails with
 * {@code DataIntegrityViolationException} — retried up to
 * {@value #MAX_ATTEMPTS} times, since the very next attempt's UPDATE will find
 * the row the winning thread just created and succeed as a plain, already-
 * proven-safe update.
 *
 * Both repository methods are pure native queries — no JPA entity is ever
 * loaded, saved, or attached to a persistence context on this path — so there
 * is no Hibernate first-level-cache staleness risk from mixing an ORM-managed
 * write with a native one in the same transaction.
 *
 * Each attempt's "try update, else try insert" pair runs in its own, genuinely
 * new transaction via {@code TransactionTemplate} with
 * {@code PROPAGATION_REQUIRES_NEW} — deliberately not
 * {@code @Transactional(propagation = REQUIRES_NEW)} on a same-class helper
 * method, since Spring's proxy-based AOP does not intercept a method calling
 * another method on {@code this} within the same instance; that would compile
 * but silently run everything in one shared transaction, defeating the retry.
 */
@Service
public class UsageTrackingService {

    private static final Logger log = LoggerFactory.getLogger(UsageTrackingService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final CurrencyUsageRepository currencyUsageRepository;
    private final TransactionTemplate requiresNewTransaction;

    public UsageTrackingService(
            CurrencyUsageRepository currencyUsageRepository,
            PlatformTransactionManager transactionManager) {
        this.currencyUsageRepository = currencyUsageRepository;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Records a lookup for both sides of a pair — T011's atomic increment once
     * per currency, twice total, or once if {@code fromCurrency} equals
     * {@code toCurrency}.
     */
    public void recordLookup(String fromCurrency, String toCurrency, LocalDate queriedDate) {
        recordSingleCurrencyLookup(fromCurrency, queriedDate);
        if (!fromCurrency.equalsIgnoreCase(toCurrency)) {
            recordSingleCurrencyLookup(toCurrency, queriedDate);
        }
    }

    private void recordSingleCurrencyLookup(String currencyCode, LocalDate queriedDate) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                boolean updatedExistingRow =
                        requiresNewTransaction.execute(
                                status ->
                                        currencyUsageRepository.incrementUsage(currencyCode, queriedDate)
                                                > 0);
                if (Boolean.TRUE.equals(updatedExistingRow)) {
                    return;
                }
                requiresNewTransaction.executeWithoutResult(
                        status -> currencyUsageRepository.insertNewRow(currencyCode, queriedDate));
                return;
            } catch (DataIntegrityViolationException e) {
                log.warn(
                        "Attempt {}/{} to record first-ever usage for {} lost a race against a "
                                + "concurrent first insert of the same currency; retrying as an "
                                + "update: {}",
                        attempt,
                        MAX_ATTEMPTS,
                        currencyCode,
                        e.getMessage());
            }
        }
        log.error(
                "Failed to record usage for currency {} after {} attempts", currencyCode, MAX_ATTEMPTS);
        throw new UsageRecordingException(
                "Failed to record usage for " + currencyCode + " after " + MAX_ATTEMPTS + " attempts");
    }
}
