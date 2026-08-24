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

import com.exchange.exchangeratesystem.error.InsightUnavailableException;
import com.exchange.exchangeratesystem.rate.dto.HistoricalRatePoint;

/**
 * T036: no Spring context. The {@link ChatModel} underneath {@link ChatClient}
 * is a plain Mockito mock — {@code ChatClient.builder(mock)} gives a real
 * {@code ChatClient} wired to it, which is the standard way to test
 * {@code ChatClient} fluent-API usage without deep-stubbing its interface
 * directly. The series itself is supplied directly as a fixture — building
 * it from stored rate data is {@code HistoricalRateService}'s job (and its
 * own test's), not this service's; this test's job is purely "does the
 * prompt faithfully reflect whatever series it's given."
 */
class TrendInsightServiceTest {

    @Test
    void promptContainsTheActualInjectedRateValuesForTheRequestedRange() {
        LocalDate day1 = LocalDate.of(2026, 3, 1);
        LocalDate day2 = LocalDate.of(2026, 3, 2);
        BigDecimal rateDay1 = new BigDecimal("4.4978125000");
        BigDecimal rateDay2 = new BigDecimal("4.5555555556");
        List<HistoricalRatePoint> series = List.of(
                new HistoricalRatePoint(day1, null, null, rateDay1),
                new HistoricalRatePoint(day2, null, null, rateDay2));

        ChatModel chatModel = mock(ChatModel.class);
        stubHealthyModel(chatModel, "EUR/PLN rose slightly.");
        TrendInsightService service = new TrendInsightService(ChatClient.builder(chatModel));

        service.generateInsight("EUR", "PLN", day1, day2, series);

        String userMessage = capturePromptUserMessage(chatModel);
        assertThat(userMessage)
                .contains("EUR/PLN")
                .contains(day1.toString())
                .contains(day2.toString())
                .contains(formatRate(rateDay1))
                .contains(formatRate(rateDay2));
    }

    @Test
    void singleDayRangeSeriesContainsExactlyOnePointForTheModelToDescribe() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        BigDecimal rate = new BigDecimal("4.4978125000");
        List<HistoricalRatePoint> series = List.of(new HistoricalRatePoint(day, null, null, rate));

        ChatModel chatModel = mock(ChatModel.class);
        stubHealthyModel(chatModel, "A single observation.");
        TrendInsightService service = new TrendInsightService(ChatClient.builder(chatModel));

        service.generateInsight("EUR", "PLN", day, day, series);

        String userMessage = capturePromptUserMessage(chatModel);
        assertThat(userMessage)
                .contains("Number of data points available: 1")
                .contains(formatRate(rate));
    }

    @Test
    void chatModelFailureIsTranslatedIntoInsightUnavailableExceptionNotAnUnhandledOne() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        List<HistoricalRatePoint> series =
                List.of(new HistoricalRatePoint(day, null, null, new BigDecimal("4.4978125000")));

        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connection refused"));
        TrendInsightService service = new TrendInsightService(ChatClient.builder(chatModel));

        assertThatThrownBy(() -> service.generateInsight("EUR", "PLN", day, day, series))
                .isInstanceOf(InsightUnavailableException.class);
    }

    /**
     * ChatClient's internals unconditionally call getOptions().mutate() while
     * building the request, even before chatModel.call(Prompt) is invoked — a
     * bare mock's default null getOptions() NPEs there, which this service's
     * catch (RuntimeException) would mask as a false-positive "model call
     * failed". Stubbing a real ChatOptions keeps each test exercising exactly
     * what it claims to.
     */
    private static void stubHealthyModel(ChatModel chatModel, String reply) {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
    }

    private static String capturePromptUserMessage(ChatModel chatModel) {
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        return promptCaptor.getValue().getUserMessage().getText();
    }

    private static String formatRate(BigDecimal rate) {
        return rate.setScale(6, RoundingMode.HALF_UP).toPlainString();
    }
}
