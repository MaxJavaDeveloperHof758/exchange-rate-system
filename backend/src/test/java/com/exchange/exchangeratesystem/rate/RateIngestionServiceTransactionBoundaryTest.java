package com.exchange.exchangeratesystem.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Confirms the Fixer.io call inside {@link RateIngestionService#ingestLatestRates()}
 * genuinely runs with no transaction open at all — not merely "no JDBC
 * connection has been checked out yet," which was the previous
 * implementation's own justification for wrapping the whole method in
 * {@code @Transactional}. Deliberately has no class-level
 * {@code @Transactional}/{@code @Rollback} (unlike {@link RateIngestionServiceTest}):
 * that would make an outer test transaction already active for the entire
 * test method, making the property this test checks unobservable.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "fixer.api-key=test-key",
            "spring.datasource.url=jdbc:h2:mem:rate-ingestion-tx-boundary-test;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class RateIngestionServiceTransactionBoundaryTest {

    @MockitoBean
    private FixerClient fixerClient;

    @Autowired
    private RateIngestionService rateIngestionService;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @AfterEach
    void cleanUp() {
        exchangeRateRepository.deleteAll();
    }

    @Test
    void fixerIoCallRunsWithNoTransactionOpen() {
        AtomicBoolean transactionWasActiveDuringFetch = new AtomicBoolean(true);
        when(fixerClient.fetchLatestRates())
                .thenAnswer(
                        invocation -> {
                            transactionWasActiveDuringFetch.set(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            return new FixerRatesResult(
                                    LocalDate.of(2026, 3, 15), Map.of("EUR", new BigDecimal("0.80")));
                        });

        rateIngestionService.ingestLatestRates();

        assertThat(transactionWasActiveDuringFetch).isFalse();
        // The persist step itself must still have actually run, transactionally.
        assertThat(exchangeRateRepository.count()).isEqualTo(1);
    }
}
