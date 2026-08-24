package com.exchange.exchangeratesystem.insight;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.exchange.exchangeratesystem.error.InsightUnavailableException;
import com.exchange.exchangeratesystem.rate.dto.HistoricalRatePoint;

/**
 * T035: generates a short, grounded natural-language commentary on a currency
 * pair's rate movement over a date range, per contracts/insight.md and
 * constitution Principle X. Takes the exact same spread-adjusted
 * {@code (date, rate)} series {@code GET /api/exchange/history} would return
 * as a parameter — built once by {@code HistoricalRateService} and passed in
 * by {@code InsightController} — rather than building its own copy of that
 * series independently; the two used to duplicate the identical
 * series-construction logic (same-currency short-circuit, missing-date
 * skipping, spread calculation), a genuine risk of the two silently
 * diverging on a future change to either. This service's only job now is
 * turning an already-computed series into a grounded prompt.
 */
@Service
public class TrendInsightService {

    private static final int DISPLAY_SCALE = 6;

    private static final String SYSTEM_PROMPT =
            """
            You are a financial data assistant that writes short, factual commentary on \
            currency exchange rate movements for an internal dashboard.

            You will be given a currency pair, a date range, and the exact series of \
            spread-adjusted exchange rates for every date in that range for which data exists. \
            Follow these rules strictly:
            - Base every statement only on the data provided. Never invent, guess, or assume \
            values that are not given.
            - Write exactly 2 to 4 sentences. No more, no less.
            - Reference the actual direction and, where it can be computed from the given \
            numbers, the approximate magnitude of the movement (e.g. a percentage or absolute \
            change).
            - Do not include generic filler, boilerplate, disclaimers, or financial advice. \
            Every sentence must say something that is specifically true of this data, not \
            something that would be equally true regardless of the input.
            - If the series contains exactly one data point, describe it as a single observation \
            for that one date — never frame a single point as an increase, decrease, or trend.
            - If the series contains many data points, do not enumerate them individually — give \
            a concise, coherent summary of the overall movement instead.
            """;

    private final ChatClient chatClient;

    public TrendInsightService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * @param series the already-computed spread-adjusted series for this
     *     exact {@code from}/{@code to}/{@code fromDate}/{@code toDate} —
     *     the caller is responsible for it being non-empty and for it
     *     actually corresponding to this range (this service does not
     *     re-verify either).
     */
    public String generateInsight(
            String from, String to, LocalDate fromDate, LocalDate toDate, List<HistoricalRatePoint> series) {
        String userMessage = buildUserMessage(from, to, fromDate, toDate, series);

        try {
            return chatClient.prompt().system(SYSTEM_PROMPT).user(userMessage).call().content();
        } catch (RuntimeException e) {
            throw new InsightUnavailableException(e);
        }
    }

    private String buildUserMessage(
            String from,
            String to,
            LocalDate fromDate,
            LocalDate toDate,
            List<HistoricalRatePoint> series) {
        StringBuilder message = new StringBuilder();
        message
                .append("Currency pair: ").append(from).append('/').append(to).append('\n')
                .append("Requested date range: ").append(fromDate).append(" to ").append(toDate)
                .append('\n')
                .append("Number of data points available: ").append(series.size()).append('\n');

        if (!series.isEmpty()) {
            HistoricalRatePoint first = series.get(0);
            HistoricalRatePoint last = series.get(series.size() - 1);
            message
                    .append("First available date: ").append(first.date()).append(" = ")
                    .append(formatRate(first.exchange())).append('\n')
                    .append("Last available date: ").append(last.date()).append(" = ")
                    .append(formatRate(last.exchange())).append('\n');
        }

        message.append("Full series (date = exchange rate):\n");
        for (HistoricalRatePoint point : series) {
            message.append(point.date()).append(" = ").append(formatRate(point.exchange())).append('\n');
        }
        return message.toString();
    }

    /** Rounds only for prompt legibility/token economy — every actual calculation upstream stays full-precision BigDecimal. */
    private static String formatRate(BigDecimal rate) {
        return rate.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
