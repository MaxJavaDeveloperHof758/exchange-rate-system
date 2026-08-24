package com.exchange.exchangeratesystem.rate;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires the daily Fixer.io ingestion job at 00:05 GMT (brief Section 4.1: "0 5
 * 0 * * *" = second 0, minute 5, hour 0 → 00:05:00, every day). Scheduling
 * itself is already enabled via {@code @EnableScheduling} on SchedulingConfig
 * (T014) — this class only needs the trigger.
 *
 * {@code @SchedulerLock} makes this safe to run on more than one application
 * instance sharing the same database: every instance's {@code @Scheduled}
 * trigger still fires at 00:05 GMT, but only the one that wins the DB-backed
 * lock actually calls {@code ingestLatestRates()} — the rest observe the lock
 * held and return immediately. {@code lockAtLeastFor} covers ordinary
 * clock skew between instances (a node whose local clock runs a few seconds
 * ahead must not be able to acquire a second lock right after the first
 * finishes); {@code lockAtMostFor} is a safety ceiling so a crashed holder
 * doesn't block every future firing forever, well above this job's normal
 * runtime (one Fixer.io call plus a handful of upserts).
 */
@Component
public class RateIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RateIngestionScheduler.class);

    private final RateIngestionService rateIngestionService;

    public RateIngestionScheduler(RateIngestionService rateIngestionService) {
        this.rateIngestionService = rateIngestionService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "GMT")
    @SchedulerLock(name = "ingestDailyRates", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    public void ingestDailyRates() {
        LockAssert.assertLocked();
        try {
            rateIngestionService.ingestLatestRates();
        } catch (RuntimeException e) {
            // Caught explicitly rather than relying solely on Spring's default
            // scheduler error handling: a failed run must never cancel future
            // scheduled runs. RateIngestionService has already logged the
            // failure's own detail (fetch failure or rolled-back batch); this
            // log records that the scheduled trigger itself absorbed it and
            // will try again at the next firing.
            log.error(
                    "Scheduled rate ingestion run failed; will retry at the next "
                            + "scheduled firing (00:05 GMT): {}",
                    e.getMessage());
        }
    }
}
