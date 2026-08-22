package com.exchange.exchangeratesystem.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

import org.hibernate.annotations.Check;

/**
 * Running count of successful lookups a given currency has participated in, on
 * either side of the pair. Keyed by currency (one row per currency ever queried).
 *
 * queryCount MUST only ever increase, by exactly 1 per successful lookup, via the
 * atomic upsert-and-increment repository query (research.md Decision 5) — never via
 * a read-modify-write on this entity, which is why no setter is exposed for it. See
 * data-model.md.
 */
@Entity
@Table(name = "currency_usage")
@Check(constraints = "query_count >= 0")
public class CurrencyUsage {

    @Id
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "query_count", columnDefinition = "BIGINT DEFAULT 0", nullable = false)
    private Long queryCount;

    @Column(name = "last_queried_date", nullable = false)
    private LocalDate lastQueriedDate;

    protected CurrencyUsage() {
        // JPA
    }

    public CurrencyUsage(String currencyCode, LocalDate lastQueriedDate) {
        this.currencyCode = currencyCode;
        this.queryCount = 0L;
        this.lastQueriedDate = lastQueriedDate;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Long getQueryCount() {
        return queryCount;
    }

    public LocalDate getLastQueriedDate() {
        return lastQueriedDate;
    }
}
