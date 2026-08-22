package com.exchange.exchangeratesystem.rate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByCurrencyCodeAndRateDate(String currencyCode, LocalDate rateDate);

    Optional<ExchangeRate> findTopByCurrencyCodeOrderByRateDateDesc(String currencyCode);

    List<ExchangeRate> findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
            String currencyCode, LocalDate start, LocalDate end);
}
