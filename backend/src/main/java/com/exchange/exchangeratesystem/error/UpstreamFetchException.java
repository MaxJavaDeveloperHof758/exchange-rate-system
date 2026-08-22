package com.exchange.exchangeratesystem.error;

/**
 * The upstream provider (Fixer.io) call failed — maps to
 * {@code 502 UPSTREAM_FETCH_FAILED}. Distinct from {@code rate.FixerClientException}
 * (T015): that one is FixerClient's own internal error boundary; this one is
 * the HTTP-facing translation of it (or of any other ingestion failure) for
 * the optional manual-refresh endpoint (T028), which is why it carries a
 * cause-chaining constructor the other two ApiException subclasses don't need.
 */
public class UpstreamFetchException extends ApiException {

    public UpstreamFetchException(String message) {
        super("UPSTREAM_FETCH_FAILED", message);
    }

    public UpstreamFetchException(String message, Throwable cause) {
        super("UPSTREAM_FETCH_FAILED", message, cause);
    }
}
