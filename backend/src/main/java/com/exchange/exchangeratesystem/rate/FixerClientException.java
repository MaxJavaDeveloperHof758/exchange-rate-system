package com.exchange.exchangeratesystem.rate;

/**
 * Raised when a Fixer.io {@code /latest} fetch cannot be completed or trusted —
 * network/HTTP failure, an API-level {@code success: false} response, or a
 * response missing the fields the ingestion path depends on.
 *
 * Deliberately not the {@code error} package's future UpstreamFetchException
 * (T020/T021, not yet implemented) — that exception's job is mapping a failure
 * to the HTTP status of the optional manual-refresh endpoint (T028); this one is
 * this client's own local error boundary, callable before that package exists,
 * and can be caught/translated at whichever later boundary needs an HTTP status.
 */
public class FixerClientException extends RuntimeException {

    public FixerClientException(String message) {
        super(message);
    }

    public FixerClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
