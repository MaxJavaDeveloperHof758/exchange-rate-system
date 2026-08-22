package com.exchange.exchangeratesystem.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.exchange.exchangeratesystem.error.ErrorResponse;
import com.exchange.exchangeratesystem.rate.ExchangeRateRepository;
import com.exchange.exchangeratesystem.rate.SpreadCalculationService;
import com.exchange.exchangeratesystem.rate.dto.ExchangeRateResponse;
import com.exchange.exchangeratesystem.usage.CurrencyUsageRepository;
import com.exchange.exchangeratesystem.usage.dto.AnalyticsResponse;
import com.exchange.exchangeratesystem.usage.dto.CurrencyUsageEntry;

/**
 * T030: full-stack coverage of {@code GET /api/exchange} through the real
 * {@code DispatcherServlet}/{@code GlobalExceptionHandler}/repository/H2 stack
 * (MockMvc, not a mocked repository) — the success path's counter increment is
 * asserted indirectly through a follow-up real {@code GET /api/analytics} call,
 * exactly as a client would observe it, rather than by reaching into
 * {@link CurrencyUsageRepository} directly.
 *
 * The datasource is overridden to an isolated in-memory H2 instance (never the
 * real {@code backend/data/exchangedb} file) and {@code fixer.api-key} is
 * supplied because {@code WebClientConfig}'s bean requires it eagerly at
 * context startup, mirroring {@code RateIngestionServiceTest}/
 * {@code UsageTrackingServiceTest}. Deliberately not
 * {@code @Transactional}/{@code @Rollback}, for the same reason as
 * {@code UsageTrackingServiceTest}: {@code UsageTrackingService}'s writes run
 * in their own {@code REQUIRES_NEW} transactions on separate connections, so
 * an outer test transaction's rollback would never undo them anyway. Isolation
 * is manual cleanup in {@link #cleanUp()} instead.
 *
 * The response body is parsed with a locally-instantiated Jackson 2
 * {@code ObjectMapper} rather than an {@code @Autowired} one: Spring Boot
 * 4.1's default JSON engine is Jackson 3 ({@code tools.jackson.databind}), so
 * no {@code com.fasterxml.jackson.databind.ObjectMapper} bean exists in the
 * context to inject. The HTTP response is plain JSON text regardless of which
 * library the server used to write it, so parsing it with either library on
 * the reading side is equally valid.
 */
@SpringBootTest(
        properties = {
            "fixer.api-key=test-key",
            "spring.datasource.url=jdbc:h2:mem:exchange-rate-controller-it;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@AutoConfigureMockMvc
class ExchangeRateControllerIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private CurrencyUsageRepository currencyUsageRepository;

    @Autowired
    private SpreadCalculationService spreadCalculationService;

    @AfterEach
    void cleanUp() {
        currencyUsageRepository.deleteAll();
        exchangeRateRepository.deleteAll();
    }

    /**
     * Seeds real EUR/PLN rows for one date, hits {@code GET /api/exchange},
     * and confirms both the returned {@code exchange} value (computed the same
     * way {@link SpreadCalculationService} itself would, since the formula's
     * per-tier correctness is already exhaustively covered by
     * {@code SpreadCalculationServiceTest} — this test's job is proving the
     * wiring, not re-deriving Appendix B) and the post-increment counters are
     * then independently visible via a real {@code GET /api/analytics} call.
     */
    @Test
    void successfulLookupIncrementsCounterVisibleInAnalytics() throws Exception {
        LocalDate rateDate = LocalDate.of(2026, 3, 15);
        BigDecimal eurRate = new BigDecimal("0.80");
        BigDecimal plnRate = new BigDecimal("3.70");
        exchangeRateRepository.upsert("EUR", eurRate, rateDate);
        exchangeRateRepository.upsert("PLN", plnRate, rateDate);
        BigDecimal expectedExchange =
                spreadCalculationService.calculate("PLN", "EUR", plnRate, eurRate);

        MvcResult exchangeResult =
                mockMvc.perform(
                                get("/api/exchange")
                                        .param("from", "EUR")
                                        .param("to", "PLN")
                                        .param("date", rateDate.toString()))
                        .andExpect(status().isOk())
                        .andReturn();
        ExchangeRateResponse response =
                OBJECT_MAPPER.readValue(
                        exchangeResult.getResponse().getContentAsString(), ExchangeRateResponse.class);

        assertThat(response.from()).isEqualTo("EUR");
        assertThat(response.to()).isEqualTo("PLN");
        assertThat(response.date()).isEqualTo(rateDate);
        assertThat(response.exchange()).isEqualByComparingTo(expectedExchange);
        assertThat(response.fromQueryCount()).isEqualTo(1L);
        assertThat(response.toQueryCount()).isEqualTo(1L);

        MvcResult analyticsResult =
                mockMvc.perform(get("/api/analytics")).andExpect(status().isOk()).andReturn();
        AnalyticsResponse analytics =
                OBJECT_MAPPER.readValue(
                        analyticsResult.getResponse().getContentAsString(), AnalyticsResponse.class);

        assertThat(analytics.topCurrencies())
                .extracting(CurrencyUsageEntry::currency, CurrencyUsageEntry::totalCount)
                .containsExactlyInAnyOrder(tuple("EUR", 1L), tuple("PLN", 1L));
    }

    /**
     * The resolved date has no stored rate for either currency (data exists on
     * other dates, proving the lookup — not the currencies — is what's
     * missing). Also confirms the "no increment on failure" contract note by
     * checking no usage row was created at all.
     */
    @Test
    void missingDateReturns404AndDoesNotIncrementUsage() throws Exception {
        LocalDate seededDate = LocalDate.of(2026, 3, 15);
        LocalDate missingDate = LocalDate.of(2099, 1, 1);
        exchangeRateRepository.upsert("EUR", new BigDecimal("0.80"), seededDate);
        exchangeRateRepository.upsert("PLN", new BigDecimal("3.70"), seededDate);

        MvcResult result =
                mockMvc.perform(
                                get("/api/exchange")
                                        .param("from", "EUR")
                                        .param("to", "PLN")
                                        .param("date", missingDate.toString()))
                        .andExpect(status().isNotFound())
                        .andReturn();
        ErrorResponse error =
                OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertThat(error.error()).isEqualTo("RATE_NOT_AVAILABLE");
        assertThat(currencyUsageRepository.count()).isZero();
    }

    /** {@code from}/{@code to} outside {@code CurrencyCode}'s recognized set, per FR-010. */
    @Test
    void unknownCurrencyCodeReturns400AndDoesNotIncrementUsage() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get("/api/exchange")
                                        .param("from", "ZZZ")
                                        .param("to", "PLN")
                                        .param("date", "2026-03-15"))
                        .andExpect(status().isBadRequest())
                        .andReturn();
        ErrorResponse error =
                OBJECT_MAPPER.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertThat(error.error()).isEqualTo("UNKNOWN_CURRENCY");
        assertThat(currencyUsageRepository.count()).isZero();
    }
}
