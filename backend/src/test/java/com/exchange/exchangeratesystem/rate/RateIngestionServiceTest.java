package com.exchange.exchangeratesystem.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.exchange.exchangeratesystem.support.PostgresTestContainerConfig;

/**
 * FixerClient is mocked; ExchangeRateRepository and the real JPA/transaction
 * machinery are not — this exercises RateIngestionService's actual upsert-loop
 * and transactional behavior against a real (Testcontainers-provided) database,
 * not a fully-mocked repository, per research.md Decision 3 (FR-003/FR-004/
 * NFR-003/SC-004: idempotency) and T016's rollback contract (NFR-004).
 *
 * fixer.api-key is overridden since WebClientConfig's bean eagerly requires
 * it at context startup and a real environment variable shouldn't be a
 * precondition for running this test. The class-level @Transactional +
 * @Rollback wraps each test method in one transaction that's always rolled
 * back afterward — tests never depend on each other's data or leave
 * anything behind.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "fixer.api-key=test-key")
@Import(PostgresTestContainerConfig.class)
@Transactional
@Rollback
class RateIngestionServiceTest {

    @MockitoBean
    private FixerClient fixerClient;

    @Autowired
    private RateIngestionService rateIngestionService;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Test
    void ingestingSameCurrencyAndDateTwiceLeavesExactlyOneRow() {
        LocalDate rateDate = LocalDate.of(2026, 3, 15);
        FixerRatesResult result =
                new FixerRatesResult(rateDate, Map.of("EUR", new BigDecimal("0.80")));
        when(fixerClient.fetchLatestRates()).thenReturn(result);

        // Two separate calls to ingestLatestRates() simulate two ingestion runs
        // for the same day — e.g. a manual retry, or two service instances both
        // firing at 00:05 GMT (FR-003/FR-004).
        rateIngestionService.ingestLatestRates();
        rateIngestionService.ingestLatestRates();

        assertThat(exchangeRateRepository.count()).isEqualTo(1);
        assertThat(exchangeRateRepository.findByCurrencyCodeAndRateDate("EUR", rateDate))
                .get()
                .extracting(ExchangeRate::getRateToUsd)
                .satisfies(rate -> assertThat(rate).isEqualByComparingTo(new BigDecimal("0.80")));
    }

    @Test
    void fixerClientFailureWritesNoData() {
        when(fixerClient.fetchLatestRates())
                .thenThrow(new FixerClientException("Fixer.io /latest was unreachable"));

        assertThatThrownBy(() -> rateIngestionService.ingestLatestRates())
                .isInstanceOf(FixerClientException.class);

        assertThat(exchangeRateRepository.count()).isZero();
    }

    /**
     * T019: distinct from {@link #fixerClientFailureWritesNoData()} above — that
     * test starts from an empty table and only proves no *new* rows appear.
     * This one proves the stronger NFR-004 claim: data that already existed
     * *before* a failed fetch is left completely unchanged — not just "still
     * present with the same value" but byte-for-byte identical, including its
     * audit timestamps, which would move if the row had been touched at all.
     */
    @Test
    void fixerClientFailureLeavesPreviouslyStoredRowsUnchanged() {
        LocalDate existingDate = LocalDate.of(2026, 3, 1);
        exchangeRateRepository.upsert("EUR", new BigDecimal("0.80"), existingDate);
        ExchangeRate before = exchangeRateRepository
                .findByCurrencyCodeAndRateDate("EUR", existingDate)
                .orElseThrow();

        when(fixerClient.fetchLatestRates())
                .thenThrow(new FixerClientException("Fixer.io /latest was unreachable"));

        // Instruction #4/#5: the exception must propagate out of
        // ingestLatestRates() (never silently swallowed), and asserting that
        // via assertThatThrownBy — rather than letting it escape the test
        // method itself — is what keeps this test from failing on an
        // uncaught exception instead of on a failed assertion.
        assertThatThrownBy(() -> rateIngestionService.ingestLatestRates())
                .isInstanceOf(FixerClientException.class);

        assertThat(exchangeRateRepository.count()).isEqualTo(1);
        ExchangeRate after = exchangeRateRepository
                .findByCurrencyCodeAndRateDate("EUR", existingDate)
                .orElseThrow();
        assertThat(after.getRateToUsd()).isEqualByComparingTo(before.getRateToUsd());
        assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    }
}
