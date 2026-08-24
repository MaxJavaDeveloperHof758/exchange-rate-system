package com.exchange.exchangeratesystem.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

import com.exchange.exchangeratesystem.currency.CurrencyCode;
import com.exchange.exchangeratesystem.error.ErrorResponse;
import com.exchange.exchangeratesystem.error.InvalidDateRangeException;
import com.exchange.exchangeratesystem.error.UnknownCurrencyException;
import com.exchange.exchangeratesystem.error.UpstreamFetchException;
import com.exchange.exchangeratesystem.rate.ExchangeRateQueryService;
import com.exchange.exchangeratesystem.rate.FixerClientException;
import com.exchange.exchangeratesystem.rate.HistoricalRateService;
import com.exchange.exchangeratesystem.rate.RateIngestionService;
import com.exchange.exchangeratesystem.rate.dto.ExchangeRateResponse;
import com.exchange.exchangeratesystem.rate.dto.HistoryResponse;
import com.exchange.exchangeratesystem.rate.dto.RefreshResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/exchange} — the spread-adjusted rate lookup (User Story 1),
 * the historical range query (User Story 2's raw-data half), and the
 * optional manual-refresh trigger (FR-022). All three per contracts/exchange.md.
 *
 * Deliberately thin: parses/validates input, delegates to
 * {@link ExchangeRateQueryService}/{@link HistoricalRateService}/
 * {@link RateIngestionService}, and returns whatever they give back — every
 * actual business rule (date resolution, series building, the same-currency
 * short-circuit, usage tracking) lives in those services now, not here.
 */
@RestController
@RequestMapping("/api/exchange")
@Tag(
        name = "Exchange Rate",
        description = "Spread-adjusted rate calculator, historical rate lookups, and manual "
                + "ingestion refresh — contracts/exchange.md.")
public class ExchangeRateController {

    private final CurrencyCode currencyCode;
    private final ExchangeRateQueryService exchangeRateQueryService;
    private final HistoricalRateService historicalRateService;
    private final RateIngestionService rateIngestionService;

    public ExchangeRateController(
            CurrencyCode currencyCode,
            ExchangeRateQueryService exchangeRateQueryService,
            HistoricalRateService historicalRateService,
            RateIngestionService rateIngestionService) {
        this.currencyCode = currencyCode;
        this.exchangeRateQueryService = exchangeRateQueryService;
        this.historicalRateService = historicalRateService;
        this.rateIngestionService = rateIngestionService;
    }

    @Operation(
            summary = "Get the spread-adjusted exchange rate for a currency pair",
            description = "Uses only locally stored data. If `date` is omitted, the most recent "
                    + "date with stored data for both currencies is used (FR-008). A "
                    + "same-currency pair (from == to) always returns exchange: 1 with no spread "
                    + "applied, and still increments that currency's usage counter once "
                    + "(FR-011).")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The spread-adjusted rate, with each currency's post-increment "
                        + "usage count.",
                content = @Content(schema = @Schema(implementation = ExchangeRateResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "`from`/`to` is not a recognized currency code, or `date` is "
                        + "malformed (FR-010).",
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
                                                    + "'date': not-a-date\"}")
                                })),
        @ApiResponse(
                responseCode = "404",
                description = "No stored rate exists for one or both currencies on the resolved "
                        + "date (FR-009).",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = "{\"error\":\"RATE_NOT_AVAILABLE\","
                                                        + "\"message\":\"No rate data available "
                                                        + "for EUR/PLN on 2024-03-15\"}")))
    })
    @GetMapping
    public ExchangeRateResponse getExchangeRate(
            @Parameter(description = "Source currency code.", example = "EUR", required = true)
                    @RequestParam
                    String from,
            @Parameter(description = "Target currency code.", example = "PLN", required = true)
                    @RequestParam
                    String to,
            @Parameter(
                    description = "Rate date (YYYY-MM-DD). If omitted, the most recent date with "
                            + "stored data for both currencies is used.",
                    example = "2024-03-15")
                    @RequestParam(required = false)
                    LocalDate date) {
        String fromCode = from.toUpperCase(Locale.ROOT);
        String toCode = to.toUpperCase(Locale.ROOT);
        validateCurrency(fromCode);
        validateCurrency(toCode);

        return exchangeRateQueryService.getRate(fromCode, toCode, date);
    }

    @Operation(
            summary = "Get raw stored rates for a currency pair over a date range",
            description = "Returns the spread-adjusted rate for every date in [startDate, "
                    + "endDate] that has usable stored data for both currencies; dates with no "
                    + "usable data are listed in `missingDates` instead of causing a failure — a "
                    + "partially incomplete range is still a 200 (User Story 2, Acceptance "
                    + "Scenario 5). Does not increment usage counters — usage tracking is scoped "
                    + "to single-pair /api/exchange lookups only.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "The raw rates found, plus any dates with no usable data.",
                content = @Content(schema = @Schema(implementation = HistoryResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Unknown currency code, malformed date, or startDate after endDate.",
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
                                                    + "'startDate': not-a-date\"}"),
                                    @ExampleObject(
                                            name = "INVALID_DATE_RANGE",
                                            value = "{\"error\":\"INVALID_DATE_RANGE\","
                                                    + "\"message\":\"startDate 2024-03-15 is after "
                                                    + "endDate 2024-03-01\"}")
                                })),
        @ApiResponse(
                responseCode = "404",
                description = "Zero usable data points exist anywhere in the requested range.",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = "{\"error\":\"RATE_NOT_AVAILABLE\","
                                                        + "\"message\":\"No rate data available "
                                                        + "for EUR/PLN between 2024-03-01 and "
                                                        + "2024-03-15\"}")))
    })
    @GetMapping("/history")
    public HistoryResponse getHistory(
            @Parameter(description = "Source currency code.", example = "EUR", required = true)
                    @RequestParam
                    String from,
            @Parameter(description = "Target currency code.", example = "PLN", required = true)
                    @RequestParam
                    String to,
            @Parameter(
                    description = "Inclusive start of the date range.",
                    example = "2024-03-01",
                    required = true)
                    @RequestParam
                    LocalDate startDate,
            @Parameter(
                    description = "Inclusive end of the date range.",
                    example = "2024-03-15",
                    required = true)
                    @RequestParam
                    LocalDate endDate) {
        String fromCode = from.toUpperCase(Locale.ROOT);
        String toCode = to.toUpperCase(Locale.ROOT);
        validateCurrency(fromCode);
        validateCurrency(toCode);
        if (startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException(
                    "startDate " + startDate + " is after endDate " + endDate);
        }

        return historicalRateService.getHistory(fromCode, toCode, startDate, endDate);
    }

    @Operation(
            summary = "Manually trigger an out-of-schedule ingestion run (optional, FR-022)",
            description = "Reuses the exact same idempotent upsert path as the daily scheduled "
                    + "job. MUST NOT read or write any Currency Usage Counter row under any "
                    + "outcome.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "202",
                description = "Ingestion ran to completion.",
                content = @Content(schema = @Schema(implementation = RefreshResponse.class))),
        @ApiResponse(
                responseCode = "502",
                description = "The upstream provider (Fixer.io) call failed.",
                content =
                        @Content(
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value = "{\"error\":\"UPSTREAM_FETCH_FAILED\","
                                                        + "\"message\":\"Manual refresh failed: "
                                                        + "Fixer.io /latest was unreachable\"}")))
    })
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh() {
        try {
            rateIngestionService.ingestLatestRates();
        } catch (FixerClientException e) {
            throw new UpstreamFetchException(
                    "Manual refresh failed: " + e.getMessage(), e);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new RefreshResponse(Instant.now(), "COMPLETED"));
    }

    private void validateCurrency(String code) {
        if (!currencyCode.isSupported(code)) {
            throw new UnknownCurrencyException("Unknown currency code: " + code);
        }
    }
}
