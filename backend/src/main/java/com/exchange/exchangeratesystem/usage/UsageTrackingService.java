package com.exchange.exchangeratesystem.usage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
 * write with a native one in the same transaction. Each attempt's "try
 * update, else try insert" pair runs in its own, genuinely new transaction
 * via {@code TransactionTemplate} with {@code PROPAGATION_REQUIRES_NEW} —
 * deliberately not {@code @Transactional(propagation = REQUIRES_NEW)} on a
 * same-class helper method, since Spring's proxy-based AOP does not
 * intercept a method calling another method on {@code this} within the same
 * instance; that would compile but silently run everything in one shared
 * transaction, defeating the retry.
 *
 * <p><b>Cross-currency atomicity for a pair lookup</b> — a {@code REQUIRES_NEW}
 * transaction per currency means a successful first-currency increment is
 * already durably committed by the time the second currency is attempted.
 * If the second currency's recording then fails after exhausting its retry
 * budget, the first currency's increment must not be left permanently
 * applied while the overall request still fails (a client retry of the same
 * request must never be able to double-count it). A single DB transaction
 * spanning both currencies — the seemingly obvious fix — was tried first via
 * {@code PROPAGATION_NESTED} savepoints and rejected after it failed
 * empirically: Hibernate's own {@code Session}/{@code Transaction} does not
 * support nested transactions at all (confirmed against this project's
 * actual Hibernate/Spring stack — a savepoint rolls back the JDBC connection
 * cleanly, but Hibernate still marks the *whole* transaction rollback-only,
 * so the outer commit then fails with {@code UnexpectedRollbackException}
 * even on the success path). Instead, on failure this method explicitly
 * <b>compensates</b> every currency it already recorded, via
 * {@link CurrencyUsageRepository#decrementUsage} (an equally atomic,
 * concurrency-safe relative {@code -1}, composing correctly with any other
 * request incrementing/decrementing the same currency concurrently) followed
 * by a conditional {@link CurrencyUsageRepository#deleteIfZeroCount} for the
 * case where compensation was undoing that currency's very first-ever row.
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
     * {@code toCurrency}. If recording the second currency fails, the first
     * currency's already-committed increment is explicitly compensated back
     * out before the failure is re-thrown, so the pair is never left
     * half-recorded.
     */
    public void recordLookup(String fromCurrency, String toCurrency, LocalDate queriedDate) {
        List<String> recorded = new ArrayList<>(2);
        try {
            recordSingleCurrencyLookup(fromCurrency, queriedDate);
            recorded.add(fromCurrency);
            if (!fromCurrency.equalsIgnoreCase(toCurrency)) {
                recordSingleCurrencyLookup(toCurrency, queriedDate);
                recorded.add(toCurrency);
            }
        } catch (RuntimeException e) {
            for (String currencyCode : recorded) {
                compensate(currencyCode);
            }
            throw e;
        }
    }

    /**
     * Undoes exactly the +1 this call itself applied to {@code currencyCode}
     * moments ago. The decrement alone is correct regardless of whether that
     * +1 landed on an existing row or created a brand-new one (both leave
     * the count exactly 1 higher than before); the delete only actually
     * removes anything in the brand-new-row case, and only if no other
     * concurrent request incremented the same currency again in between —
     * its {@code WHERE query_count = 0} condition is evaluated atomically
     * against the row's live state, so it simply won't match otherwise.
     */
    private void compensate(String currencyCode) {
        try {
            requiresNewTransaction.executeWithoutResult(
                    status -> currencyUsageRepository.decrementUsage(currencyCode));
            requiresNewTransaction.executeWithoutResult(
                    status -> currencyUsageRepository.deleteIfZeroCount(currencyCode));
        } catch (RuntimeException e) {
            // Do not let a compensation failure mask the original failure that
            // triggered it — log it as its own, distinct data-consistency gap.
            log.error(
                    "Failed to compensate usage count for {} after a pair lookup failed partway "
                            + "through — its count may now be 1 higher than it should be: {}",
                    currencyCode,
                    e.getMessage());
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
