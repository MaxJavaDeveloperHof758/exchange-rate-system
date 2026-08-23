package com.exchange.exchangeratesystem.insight;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.exchange.exchangeratesystem.error.InsightUnavailableException;
import com.exchange.exchangeratesystem.rate.ExchangeRate;
import com.exchange.exchangeratesystem.rate.ExchangeRateRepository;
import com.exchange.exchangeratesystem.rate.SpreadCalculationService;

/**
 * T035: generates a short, grounded natural-language commentary on a currency
 * pair's rate movement over a date range, per contracts/insight.md and
 * constitution Principle X. Builds the exact same spread-adjusted
 * {@code (date, rate)} series {@code ExchangeRateController#getHistory} would
 * return — via {@link ExchangeRateRepository} directly, not by calling the
 * controller — then serializes that real series into the prompt so the model
 * is responding to actual injected numbers, never a template.
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

    private final ExchangeRateRepository exchangeRateRepository;
    private final SpreadCalculationService spreadCalculationService;
    private final ChatClient chatClient;

    public TrendInsightService(
            ExchangeRateRepository exchangeRateRepository,
            SpreadCalculationService spreadCalculationService,
            ChatClient.Builder chatClientBuilder) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.spreadCalculationService = spreadCalculationService;
        this.chatClient = chatClientBuilder.build();
    }

    public String generateInsight(String from, String to, LocalDate fromDate, LocalDate toDate) {
        List<RatePoint> series = buildSeries(from, to, fromDate, toDate);
        String userMessage = buildUserMessage(from, to, fromDate, toDate, series);

        try {
            return chatClient.prompt().system(SYSTEM_PROMPT).user(userMessage).call().content();
        } catch (RuntimeException e) {
            throw new InsightUnavailableException(e);
        }
    }

    /**
     * Mirrors {@code ExchangeRateController#getHistory}'s series construction
     * exactly (same-currency short-circuit to a constant 1, missing dates
     * silently skipped rather than failing) so the model is grounded in
     * precisely what a client would see via {@code /api/exchange/history} for
     * this same range.
     */
    private List<RatePoint> buildSeries(String from, String to, LocalDate fromDate, LocalDate toDate) {
        List<RatePoint> points = new ArrayList<>();

        if (from.equalsIgnoreCase(to)) {
            for (LocalDate cursor = fromDate; !cursor.isAfter(toDate); cursor = cursor.plusDays(1)) {
                points.add(new RatePoint(cursor, BigDecimal.ONE));
            }
            return points;
        }

        Map<LocalDate, BigDecimal> fromRates = ratesByDate(from, fromDate, toDate);
        Map<LocalDate, BigDecimal> toRates = ratesByDate(to, fromDate, toDate);

        for (LocalDate cursor = fromDate; !cursor.isAfter(toDate); cursor = cursor.plusDays(1)) {
            BigDecimal fromRateToUsd = fromRates.get(cursor);
            BigDecimal toRateToUsd = toRates.get(cursor);
            if (fromRateToUsd != null && toRateToUsd != null) {
                BigDecimal adjustedRate =
                        spreadCalculationService.calculate(to, from, toRateToUsd, fromRateToUsd);
                points.add(new RatePoint(cursor, adjustedRate));
            }
        }
        return points;
    }

    private Map<LocalDate, BigDecimal> ratesByDate(String code, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, BigDecimal> byDate = new HashMap<>();
        for (ExchangeRate rate :
                exchangeRateRepository.findByCurrencyCodeAndRateDateBetweenOrderByRateDateAsc(
                        code, startDate, endDate)) {
            byDate.put(rate.getRateDate(), rate.getRateToUsd());
        }
        return byDate;
    }

    private String buildUserMessage(
            String from, String to, LocalDate fromDate, LocalDate toDate, List<RatePoint> series) {
        StringBuilder message = new StringBuilder();
        message
                .append("Currency pair: ").append(from).append('/').append(to).append('\n')
                .append("Requested date range: ").append(fromDate).append(" to ").append(toDate)
                .append('\n')
                .append("Number of data points available: ").append(series.size()).append('\n');

        if (!series.isEmpty()) {
            RatePoint first = series.get(0);
            RatePoint last = series.get(series.size() - 1);
            message
                    .append("First available date: ").append(first.date()).append(" = ")
                    .append(formatRate(first.rate())).append('\n')
                    .append("Last available date: ").append(last.date()).append(" = ")
                    .append(formatRate(last.rate())).append('\n');
        }

        message.append("Full series (date = exchange rate):\n");
        for (RatePoint point : series) {
            message.append(point.date()).append(" = ").append(formatRate(point.rate())).append('\n');
        }
        return message.toString();
    }

    /** Rounds only for prompt legibility/token economy — every actual calculation above stays full-precision BigDecimal. */
    private static String formatRate(BigDecimal rate) {
        return rate.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private record RatePoint(LocalDate date, BigDecimal rate) {
    }
}
