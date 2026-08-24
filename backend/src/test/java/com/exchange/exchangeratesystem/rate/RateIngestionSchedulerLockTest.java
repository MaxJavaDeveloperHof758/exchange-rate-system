package com.exchange.exchangeratesystem.rate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves ShedLock's mutual exclusion actually works end to end, not just
 * that the annotations compile: two concurrent calls to the real,
 * AOP-proxied {@link RateIngestionScheduler#ingestDailyRates()} contend for
 * the same database-backed lock row, exactly as two application instances
 * pointed at the same database would at 00:05 GMT. The exclusion here is
 * enforced by a row in the {@code shedlock} table (schema.sql), not by JVM
 * thread synchronization, so a single test process genuinely exercises the
 * multi-instance guarantee.
 */
@SpringBootTest(
        properties = {
            "fixer.api-key=test-key",
            "spring.datasource.url=jdbc:h2:mem:rate-ingestion-scheduler-lock;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
class RateIngestionSchedulerLockTest {

    @Autowired
    private RateIngestionScheduler scheduler;

    @MockitoBean
    private RateIngestionService rateIngestionService;

    /**
     * The first call is held mid-flight (simulating a slow/in-progress
     * ingestion) while a second, concurrent call attempts the same lock.
     * ShedLock's scheduled-task strategy is non-blocking — a caller that
     * can't acquire the lock skips the method body entirely rather than
     * waiting for it — so the second call must return immediately having
     * never invoked {@code ingestLatestRates()} at all.
     */
    @Test
    void secondConcurrentCallIsSkippedWhileTheFirstHoldsTheLock() throws InterruptedException {
        CountDownLatch firstCallEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstCall = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();

        doAnswer(invocation -> {
                    invocations.incrementAndGet();
                    firstCallEntered.countDown();
                    releaseFirstCall.await(5, TimeUnit.SECONDS);
                    return null;
                })
                .when(rateIngestionService)
                .ingestLatestRates();

        Thread first = new Thread(scheduler::ingestDailyRates);
        first.start();
        assertThat(firstCallEntered.await(2, TimeUnit.SECONDS))
                .as("first call should have entered ingestLatestRates before the second is attempted")
                .isTrue();

        scheduler.ingestDailyRates();
        assertThat(invocations.get())
                .as("a concurrent call must not invoke ingestion while the lock is held")
                .isEqualTo(1);

        releaseFirstCall.countDown();
        first.join(5_000);
        assertThat(invocations.get()).isEqualTo(1);
    }
}
