package com.exchange.exchangeratesystem.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Deliberately NOT {@code @Transactional}/{@code @Rollback}: UsageTrackingService's
 * retry logic runs each attempt in a genuinely new transaction (via
 * TransactionTemplate + PROPAGATION_REQUIRES_NEW) on a separate pooled
 * connection. If this test class wrapped each test method in its own outer
 * transaction, pre-seeded data would be invisible to the concurrent threads'
 * separately-committing inner transactions under READ_COMMITTED isolation.
 * Isolation is achieved instead via an in-memory datasource scoped to this
 * test class and manual cleanup in {@link #cleanUp()}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "fixer.api-key=test-key",
            "spring.datasource.url=jdbc:h2:mem:usage-tracking-service-test;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class UsageTrackingServiceTest {

    private static final int CONCURRENT_REQUESTS = 50;

    @Autowired
    private UsageTrackingService usageTrackingService;

    @Autowired
    private CurrencyUsageRepository currencyUsageRepository;

    @AfterEach
    void cleanUp() {
        currencyUsageRepository.deleteAll();
    }

    /** T025: at least 50 concurrent recordLookup calls for the same currency (a brand-new one). */
    @Test
    void fiftyConcurrentLookupsForABrandNewCurrencyYieldExactlyFifty() throws InterruptedException {
        String currencyCode = "JPY";
        LocalDate queriedDate = LocalDate.of(2026, 3, 1);

        runConcurrently(
                CONCURRENT_REQUESTS,
                () -> usageTrackingService.recordLookup(currencyCode, currencyCode, queriedDate));

        long finalCount = currencyUsageRepository.findById(currencyCode).orElseThrow().getQueryCount();
        assertThat(finalCount).isEqualTo(CONCURRENT_REQUESTS);
    }

    /** Same 50-concurrent scenario, but the currency already has a row before the run starts. */
    @Test
    void fiftyConcurrentLookupsForAnExistingCurrencyYieldExactlyFifty() throws InterruptedException {
        String currencyCode = "EUR";
        currencyUsageRepository.insertNewRow(currencyCode, LocalDate.of(2026, 1, 1));
        long seededCount =
                currencyUsageRepository.findById(currencyCode).orElseThrow().getQueryCount();

        runConcurrently(
                CONCURRENT_REQUESTS,
                () ->
                        usageTrackingService.recordLookup(
                                currencyCode, currencyCode, LocalDate.of(2026, 3, 1)));

        long finalCount = currencyUsageRepository.findById(currencyCode).orElseThrow().getQueryCount();
        assertThat(finalCount).isEqualTo(seededCount + CONCURRENT_REQUESTS);
    }

    @Test
    void pairLookupIncrementsBothDistinctCurrenciesExactlyOnce() {
        LocalDate queriedDate = LocalDate.of(2026, 3, 15);

        usageTrackingService.recordLookup("EUR", "PLN", queriedDate);

        assertThat(currencyUsageRepository.findById("EUR").orElseThrow().getQueryCount())
                .isEqualTo(1);
        assertThat(currencyUsageRepository.findById("PLN").orElseThrow().getQueryCount())
                .isEqualTo(1);
    }

    @Test
    void pairLookupWithSameCurrencyOnBothSidesIncrementsOnlyOnce() {
        LocalDate queriedDate = LocalDate.of(2026, 3, 15);

        usageTrackingService.recordLookup("EUR", "EUR", queriedDate);

        assertThat(currencyUsageRepository.findById("EUR").orElseThrow().getQueryCount())
                .isEqualTo(1);
        assertThat(currencyUsageRepository.count()).isEqualTo(1);
    }

    private void runConcurrently(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(
                    () -> {
                        ready.countDown();
                        try {
                            go.await();
                            task.run();
                        } catch (Throwable t) {
                            errors.add(t);
                        } finally {
                            done.countDown();
                        }
                    });
        }

        ready.await();
        go.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).as("all threads completed within the timeout").isTrue();
        assertThat(errors).as("no thread threw an exception").isEmpty();
    }
}
