package com.exchange.exchangeratesystem.usage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.exchange.exchangeratesystem.support.PostgresTestContainerConfig;

/**
 * Confirms the cross-currency compensation UsageTrackingService's class
 * Javadoc describes: if the second currency of a pair lookup fails after
 * exhausting its retry budget, the first currency's already-committed
 * increment must be explicitly undone, not left permanently applied while
 * the overall request still fails.
 *
 * {@link CurrencyUsageRepository} is mocked (not the whole Spring context)
 * so both currencies' outcomes are fully deterministic — PLN's every
 * attempt is made to lose the insert race, exhausting all 3 retries — while
 * {@code UsageTrackingService} still runs against a real
 * {@code PlatformTransactionManager} from a real (otherwise-unused,
 * Testcontainers-provided) Postgres context, exercising its actual
 * transaction-template wiring rather than a fully-mocked stand-in for it.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "fixer.api-key=test-key")
@Import(PostgresTestContainerConfig.class)
class UsageTrackingServiceCompensationTest {

    @MockitoBean
    private CurrencyUsageRepository currencyUsageRepository;

    @Autowired
    private UsageTrackingService usageTrackingService;

    @Test
    void firstCurrencysIncrementIsCompensatedWhenTheSecondCurrencyFailsAfterExhaustingRetries() {
        LocalDate queriedDate = LocalDate.of(2026, 3, 15);

        // EUR ("from"): succeeds immediately as a plain existing-row update.
        when(currencyUsageRepository.incrementUsage(eq("EUR"), eq(queriedDate))).thenReturn(1);

        // PLN ("to"): every attempt's update reports "no existing row," and
        // every attempt's insert loses the race - exhausting all 3 retries
        // and forcing UsageRecordingException, deterministically rather than
        // depending on a real concurrent race actually landing this way.
        when(currencyUsageRepository.incrementUsage(eq("PLN"), eq(queriedDate))).thenReturn(0);
        doThrow(new DataIntegrityViolationException("simulated race"))
                .when(currencyUsageRepository)
                .insertNewRow(eq("PLN"), eq(queriedDate));

        assertThatThrownBy(() -> usageTrackingService.recordLookup("EUR", "PLN", queriedDate))
                .isInstanceOf(UsageRecordingException.class);

        // EUR's earlier, already-committed increment must be compensated back out.
        verify(currencyUsageRepository).decrementUsage("EUR");
        verify(currencyUsageRepository).deleteIfZeroCount("EUR");
        // PLN was never successfully recorded in the first place - compensating
        // it too would itself be a bug (there is nothing to undo).
        verify(currencyUsageRepository, never()).decrementUsage("PLN");
        verify(currencyUsageRepository, never()).deleteIfZeroCount("PLN");
    }
}
