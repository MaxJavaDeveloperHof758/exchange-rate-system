package com.exchange.exchangeratesystem.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import com.exchange.exchangeratesystem.currency.CurrencyCode;
import com.exchange.exchangeratesystem.error.ErrorResponse;
import com.exchange.exchangeratesystem.error.InvalidDateRangeException;
import com.exchange.exchangeratesystem.error.RateNotAvailableException;
import com.exchange.exchangeratesystem.error.UnknownCurrencyException;
import com.exchange.exchangeratesystem.insight.TrendInsightService;
import com.exchange.exchangeratesystem.insight.dto.InsightResponse;
import com.exchange.exchangeratesystem.rate.HistoricalRateService;
import com.exchange.exchangeratesystem.rate.dto.HistoricalRatePoint;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/exchange/insight} — the AI-generated trend commentary (T037,
 * User Story 2's AI half), per contracts/insight.md. A separate controller
 * from {@link ExchangeRateController} despite sharing the {@code /api/exchange}
 * path prefix, per T037's own file layout. Never reads or writes any Currency
 * Usage Counter row (FR-011's scope is {@code /api/exchange} lookups only).
 *
 * Builds the series once via {@link HistoricalRateService} (the same
 * component {@code GET /api/exchange/history} uses) and passes it straight
 * into {@link TrendInsightService} — the two no longer each build their own
 * copy of it.
 */
@RestController
@RequestMapping("/api/exchange")
@Tag(
        name = "Trend Insight",
        description = "AI-generated commentary on a currency pair's rate movement over a date "
                + "range, grounded in the real stored series — contracts/insight.md.")
public class InsightController {

    private final CurrencyCode currencyCode;
    private final HistoricalRateService historicalRateService;
    private final TrendInsightService trendInsightService;

    public InsightController(
            CurrencyCode currencyCode,
            HistoricalRateService historicalRateService,
            TrendInsightService trendInsightService) {
        this.currencyCode = currencyCode;
        this.historicalRateService = historicalRateService;
        this.trendInsightService = trendInsightService;
    }

    @Operation(
            summary = "Get an AI-generated trend commentary for a currency pair's rate movement",
            description = "Grounds the model in the exact same (date, rate) series "
                    + "GET /api/exchange/history would return for this from/to/fromDate/toDate "
                    + "(constitution Principle X) — never a generic or templated response. A "
                    + "single-day range (fromDate == toDate) is described as one observation, "
                    + "not a multi-day trend. Has no side effects on usage counters and is not "
                    + "cached or persisted server-side.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The generated trend commentary.",
                content = @Content(schema = @Schema(implementation = InsightResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Unknown currency code, malformed date, or fromDate after toDate.",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples = {
                                    @ExampleObject(
                                            name = "UNKNOWN_CURRENCY",
                                            value = "{\"error\":\"UNKNOWN_CURRENCY\","
                                                    + "\"message\":\"Unknown currency code: XXX\"}"),
                                    @ExampleObject(
                                            name = "INVALID_DATE_FORMAT",
                                            value = "{\"error\":\"INVALID_DATE_FORMAT\","
                                                    + "\"message\":\"Invalid value for parameter "
                                                    + "'fromDate': not-a-date\"}"),
                                    @ExampleObject(
                                            name = "INVALID_DATE_RANGE",
                                            value = "{\"error\":\"INVALID_DATE_RANGE\","
                                                    + "\"message\":\"fromDate 2024-03-15 is after "
                                                    + "toDate 2024-03-01\"}")
                                })),
        @ApiResponse(
                responseCode = "404",
                description = "No stored rate data exists anywhere in the requested range — "
                        + "nothing to summarize.",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = "{\"error\":\"RATE_NOT_AVAILABLE\","
                                                        + "\"message\":\"No rate data available "
                                                        + "for EUR/PLN between 2024-02-01 and "
                                                        + "2024-03-01\"}"))),
        @ApiResponse(
                responseCode = "503",
                description = "The local LLM call failed or timed out (model not running, "
                        + "connection refused, etc.). The rate data itself is unaffected and "
                        + "remains available via /api/exchange/history.",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = "{\"error\":\"INSIGHT_UNAVAILABLE\","
                                                        + "\"message\":\"The trend insight could "
                                                        + "not be generated. The rate data itself "
                                                        + "is still available via "
                                                        + "/api/exchange/history.\"}")))
    })
    @GetMapping("/insight")
    public InsightResponse getInsight(
            @Parameter(description = "Source currency code.", example = "EUR", required = true)
                    @RequestParam
                    String from,
            @Parameter(description = "Target currency code.", example = "GBP", required = true)
                    @RequestParam
                    String to,
            @Parameter(
                    description = "Inclusive start of the date range.",
                    example = "2024-02-01",
                    required = true)
                    @RequestParam
                    LocalDate fromDate,
            @Parameter(
                    description = "Inclusive end of the date range.",
                    example = "2024-03-01",
                    required = true)
                    @RequestParam
                    LocalDate toDate) {
        String fromCode = from.toUpperCase(Locale.ROOT);
        String toCode = to.toUpperCase(Locale.ROOT);
        validateCurrency(fromCode);
        validateCurrency(toCode);
        if (fromDate.isAfter(toDate)) {
            throw new InvalidDateRangeException(
                    "fromDate " + fromDate + " is after toDate " + toDate);
        }

        List<HistoricalRatePoint> series =
                historicalRateService.computeSeries(fromCode, toCode, fromDate, toDate);
        if (series.isEmpty()) {
            throw new RateNotAvailableException(
                    "No rate data available for " + fromCode + "/" + toCode + " between "
                            + fromDate + " and " + toDate);
        }

        String insight = trendInsightService.generateInsight(fromCode, toCode, fromDate, toDate, series);
        return new InsightResponse(fromCode, toCode, fromDate, toDate, insight);
    }

    private void validateCurrency(String code) {
        if (!currencyCode.isSupported(code)) {
            throw new UnknownCurrencyException("Unknown currency code: " + code);
        }
    }
}
