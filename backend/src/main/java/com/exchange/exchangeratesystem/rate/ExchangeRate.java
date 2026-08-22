package com.exchange.exchangeratesystem.rate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.Check;

/**
 * One currency's rate for one specific validity date, as reported by Fixer.io.
 * Written only by the ingestion process (scheduled or manual refresh); read-only
 * from every other component's perspective. See data-model.md.
 */
@Entity
@Table(
        name = "exchange_rate",
        uniqueConstraints = @UniqueConstraint(columnNames = {"currency_code", "rate_date"}))
@Check(constraints = "rate_to_usd > 0")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "rate_to_usd", columnDefinition = "DECIMAL(19,10)", nullable = false)
    private BigDecimal rateToUsd;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ExchangeRate() {
        // JPA
    }

    public ExchangeRate(String currencyCode, BigDecimal rateToUsd, LocalDate rateDate) {
        this.currencyCode = currencyCode;
        this.rateToUsd = rateToUsd;
        this.rateDate = rateDate;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public BigDecimal getRateToUsd() {
        return rateToUsd;
    }

    public void setRateToUsd(BigDecimal rateToUsd) {
        this.rateToUsd = rateToUsd;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public void setRateDate(LocalDate rateDate) {
        this.rateDate = rateDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
