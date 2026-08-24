package com.exchange.exchangeratesystem.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/**
 * Confirms {@link CurrencyUsageRepository#decrementUsage} and
 * {@link CurrencyUsageRepository#deleteIfZeroCount} — the compensating
 * pair {@code UsageTrackingService} uses to undo a currency's increment
 * when a pair lookup's other currency fails — behave correctly against a
 * real database, not just as mocked interactions.
 */
@DataJpaTest
class CurrencyUsageRepositoryTest {

    @Autowired
    private CurrencyUsageRepository repository;

    @Test
    void decrementUsageReducesAnExistingCountByExactlyOne() {
        repository.insertNewRow("EUR", LocalDate.of(2026, 3, 1));
        repository.incrementUsage("EUR", LocalDate.of(2026, 3, 2));

        int rowsAffected = repository.decrementUsage("EUR");

        assertThat(rowsAffected).isEqualTo(1);
        assertThat(repository.findById("EUR").orElseThrow().getQueryCount()).isEqualTo(1);
    }

    @Test
    void deleteIfZeroCountRemovesARowWhoseCountIsExactlyZero() {
        repository.insertNewRow("EUR", LocalDate.of(2026, 3, 1));
        repository.decrementUsage("EUR");

        repository.deleteIfZeroCount("EUR");

        assertThat(repository.findById("EUR")).isEmpty();
    }

    @Test
    void deleteIfZeroCountLeavesANonZeroRowUntouched() {
        repository.insertNewRow("EUR", LocalDate.of(2026, 3, 1));
        repository.incrementUsage("EUR", LocalDate.of(2026, 3, 2));

        repository.deleteIfZeroCount("EUR");

        assertThat(repository.findById("EUR"))
                .isPresent()
                .get()
                .extracting(CurrencyUsage::getQueryCount)
                .isEqualTo(2L);
    }
}
