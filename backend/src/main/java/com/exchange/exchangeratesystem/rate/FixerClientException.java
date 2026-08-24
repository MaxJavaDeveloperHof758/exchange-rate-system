package com.exchange.exchangeratesystem.rate;

/**
 * Raised when a Fixer.io {@code /latest} fetch cannot be completed or trusted —
 * network/HTTP failure, an API-level {@code success: false} response, or a
 * response missing the fields the ingestion path depends on.
 *
 * Deliberately not {@code error.UpstreamFetchException} — that exception's
 * job is mapping a failure to the HTTP status of the optional manual-refresh
 * endpoint; this one is this client's own local error boundary, and can be
 * caught/translated at whichever later boundary needs an HTTP status.
 */
public class FixerClientException extends RuntimeException {

    public FixerClientException(String message) {
        super(message);
    }

    public FixerClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
