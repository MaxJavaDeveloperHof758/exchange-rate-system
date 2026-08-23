package com.exchange.exchangeratesystem.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.exchange.exchangeratesystem.currency.CurrencySpread;
import com.exchange.exchangeratesystem.error.InsightUnavailableException;
import com.exchange.exchangeratesystem.rate.ExchangeRate;
import com.exchange.exchangeratesystem.rate.ExchangeRateRepository;
import com.exchange.exchangeratesystem.rate.SpreadCalculationService;

/**
 * T036: no Spring context — {@link ExchangeRateRepository} is a plain
 * Mockito mock (fixture rate rows), {@link SpreadCalculationService} is the
 * real implementation (so asserted rate values are the actual formula
 * output, not an arbitrary fixture), and the {@link ChatModel} underneath
 * {@link ChatClient} is a plain Mockito mock — {@code ChatClient.builder(mock)}
 * gives a real {@code ChatClient} wired to it, which is the standard way to
 * test {@code ChatClient} fluent-API usage without deep-stubbing its
 * interface directly.
 */
class TrendInsightServiceTest {

    private final ExchangeRateRepository exchangeRateRepository = mock(ExchangeRateRepository.class);
    private final SpreadCalculationService spreadCalculationService =
            new SpreadCalculationService(new CurrencySpread());

    @Test
    void promptContainsTheActualInjectedRateValuesForTheRequestedRange() {
        LocalDate day1 = LocalDate.of(2026, 3, 1);
        LocalDate day2 = LocalDate.of(2026, 3, 2);
        BigDecimal eurDay1 = new BigDecimal("0.80");
        BigDecimal eurDay2 = new BigDecimal("0.81");
        BigDecimal plnDay1 = new BigDecimal("3.70");
        BigDecimal plnDay2 = new BigDecimal("3.75");

        when(exchangeRateRepository.findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
                        "EUR", day1, day2))
                .thenReturn(List.of(
                        new ExchangeRate("EUR", eurDay1, day1), new ExchangeRate("EUR", eurDay2, day2)));
        when(exchangeRateRepository.findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
                        "PLN", day1, day2))
                .thenReturn(List.of(
                        new ExchangeRate("PLN", plnDay1, day1), new ExchangeRate("PLN", plnDay2, day2)));

        // The real formula's own output — proves the prompt carries the actual
        // computed series, not a fixture the test invented independently.
        BigDecimal expectedDay1 = spreadCalculationService.calculate("PLN", "EUR", plnDay1, eurDay1);
        BigDecimal expectedDay2 = spreadCalculationService.calculate("PLN", "EUR", plnDay2, eurDay2);

        ChatModel chatModel = mock(ChatModel.class);
        // ChatClient's internals unconditionally call getOptions().mutate() while
        // building the request, even before chatModel.call(Prompt) is invoked — a
        // bare mock's default null getOptions() NPEs there, which this service's
        // catch (RuntimeException) would mask as a false-positive "model call
        // failed". Stubbing a real ChatOptions keeps each test exercising exactly
        // what it claims to.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(
                        new ChatResponse(
                                List.of(new Generation(new AssistantMessage("EUR/PLN rose slightly.")))));
        TrendInsightService service = new TrendInsightService(
                exchangeRateRepository, spreadCalculationService, ChatClient.builder(chatModel));

        service.generateInsight("EUR", "PLN", day1, day2);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String userMessage = promptCaptor.getValue().getUserMessage().getText();

        assertThat(userMessage)
                .contains("EUR/PLN")
                .contains(day1.toString())
                .contains(day2.toString())
                .contains(formatRate(expectedDay1))
                .contains(formatRate(expectedDay2));
    }

    @Test
    void singleDayRangeSeriesContainsExactlyOnePointForTheModelToDescribe() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        BigDecimal eurRate = new BigDecimal("0.80");
        BigDecimal plnRate = new BigDecimal("3.70");
        when(exchangeRateRepository.findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
                        "EUR", day, day))
                .thenReturn(List.of(new ExchangeRate("EUR", eurRate, day)));
        when(exchangeRateRepository.findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
                        "PLN", day, day))
                .thenReturn(List.of(new ExchangeRate("PLN", plnRate, day)));
        BigDecimal expected = spreadCalculationService.calculate("PLN", "EUR", plnRate, eurRate);

        ChatModel chatModel = mock(ChatModel.class);
        // ChatClient's internals unconditionally call getOptions().mutate() while
        // building the request, even before chatModel.call(Prompt) is invoked — a
        // bare mock's default null getOptions() NPEs there, which this service's
        // catch (RuntimeException) would mask as a false-positive "model call
        // failed". Stubbing a real ChatOptions keeps each test exercising exactly
        // what it claims to.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(
                        new ChatResponse(
                                List.of(new Generation(new AssistantMessage("A single observation.")))));
        TrendInsightService service = new TrendInsightService(
                exchangeRateRepository, spreadCalculationService, ChatClient.builder(chatModel));

        service.generateInsight("EUR", "PLN", day, day);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String userMessage = promptCaptor.getValue().getUserMessage().getText();

        assertThat(userMessage)
                .contains("Number of data points available: 1")
                .contains(formatRate(expected));
    }

    @Test
    void chatModelFailureIsTranslatedIntoInsightUnavailableExceptionNotAnUnhandledOne() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        when(exchangeRateRepository.findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
                        "EUR", day, day))
                .thenReturn(List.of(new ExchangeRate("EUR", new BigDecimal("0.80"), day)));
        when(exchangeRateRepository.findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
                        "PLN", day, day))
                .thenReturn(List.of(new ExchangeRate("PLN", new BigDecimal("3.70"), day)));

        ChatModel chatModel = mock(ChatModel.class);
        // ChatClient's internals unconditionally call getOptions().mutate() while
        // building the request, even before chatModel.call(Prompt) is invoked — a
        // bare mock's default null getOptions() NPEs there, which this service's
        // catch (RuntimeException) would mask as a false-positive "model call
        // failed". Stubbing a real ChatOptions keeps each test exercising exactly
        // what it claims to.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("connection refused"));
        TrendInsightService service = new TrendInsightService(
                exchangeRateRepository, spreadCalculationService, ChatClient.builder(chatModel));

        assertThatThrownBy(() -> service.generateInsight("EUR", "PLN", day, day))
                .isInstanceOf(InsightUnavailableException.class);
    }

    private static String formatRate(BigDecimal rate) {
        return rate.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }
}
