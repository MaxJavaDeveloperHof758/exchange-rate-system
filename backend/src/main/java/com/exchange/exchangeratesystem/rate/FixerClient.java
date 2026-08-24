package com.exchange.exchangeratesystem.rate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Calls Fixer.io's {@code /latest} endpoint via the {@code fixerWebClient}
 * bean and maps the response into a {@link FixerRatesResult}. Every failure mode
 * (network failure, non-2xx response, or Fixer's own {@code success: false}
 * body convention) is logged and raised as {@link FixerClientException} — never
 * silently swallowed — so the caller ({@code RateIngestionService}) can
 * decide how to degrade (NFR-004: retain existing data, make the failure
 * observable, don't corrupt anything).
 */
@Component
public class FixerClient {

    private static final Logger log = LoggerFactory.getLogger(FixerClient.class);

    private final WebClient fixerWebClient;

    public FixerClient(WebClient fixerWebClient) {
        this.fixerWebClient = fixerWebClient;
    }

    public FixerRatesResult fetchLatestRates() {
        FixerLatestResponse response;
        try {
            response = fixerWebClient.get()
                    .uri("/latest")
                    .retrieve()
                    .bodyToMono(FixerLatestResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Fixer.io /latest returned HTTP {}: {}", e.getStatusCode(), e.getMessage());
            throw new FixerClientException(
                    "Fixer.io /latest returned HTTP " + e.getStatusCode(), e);
        } catch (WebClientRequestException e) {
            log.error("Fixer.io /latest was unreachable: {}", e.getMessage());
            throw new FixerClientException("Fixer.io /latest was unreachable", e);
        }

        if (response == null) {
            log.error("Fixer.io /latest returned an empty response body");
            throw new FixerClientException("Fixer.io /latest returned an empty response body");
        }

        if (!response.success()) {
            String reason = response.error() != null
                    ? response.error().type() + " (" + response.error().info() + ")"
                    : "unknown error";
            log.error("Fixer.io /latest reported failure: {}", reason);
            throw new FixerClientException("Fixer.io /latest reported failure: " + reason);
        }

        if (response.date() == null || response.rates() == null) {
            log.error("Fixer.io /latest response missing date or rates: {}", response);
            throw new FixerClientException("Fixer.io /latest response missing date or rates");
        }

        // The validity date comes from the response body itself, never the
        // system clock (FR-002) — LocalDate.now() must never appear here.
        return new FixerRatesResult(LocalDate.parse(response.date()), response.rates());
    }

    /** Raw JSON shape of a Fixer.io {@code /latest} response — success or error. */
    private record FixerLatestResponse(
            boolean success,
            String base,
            String date,
            Map<String, BigDecimal> rates,
            FixerErrorBody error) {
    }

    private record FixerErrorBody(int code, String type, String info) {
    }
}
