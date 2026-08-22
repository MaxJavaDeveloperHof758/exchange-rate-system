package com.exchange.exchangeratesystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support, backing the daily Fixer.io
 * ingestion job (T017, {@code RateIngestionScheduler}).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
