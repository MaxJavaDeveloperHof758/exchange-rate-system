package com.exchange.exchangeratesystem.config;

import javax.sql.DataSource;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support, backing the daily Fixer.io
 * ingestion job (T017, {@code RateIngestionScheduler}), plus a DB-backed
 * distributed lock (ShedLock) so that job only ever runs on one application
 * instance at a time when more than one instance shares the same database.
 * {@code defaultLockAtMostFor} is a safety ceiling only — the method's own
 * {@code @SchedulerLock} sets the real values; this default only matters if
 * a future {@code @SchedulerLock} method omits {@code lockAtMostFor}.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulingConfig {

    /**
     * {@code usingDbTime()} makes lock expiry compare against the
     * database's own clock rather than each instance's local clock, so
     * instance-to-instance clock skew can't cause one node to treat a lock
     * as expired while the holder still considers it valid.
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
