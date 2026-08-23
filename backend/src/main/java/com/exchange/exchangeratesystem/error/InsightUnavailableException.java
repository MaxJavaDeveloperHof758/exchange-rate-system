package com.exchange.exchangeratesystem.error;

/**
 * The local LLM call for a trend insight failed or timed out (model not
 * running, connection refused, etc.) — maps to {@code 503 INSIGHT_UNAVAILABLE}
 * per contracts/insight.md. The client-facing message is the contract's own
 * fixed wording, not the underlying cause's message: the rate data pipeline
 * (`/api/exchange/history`) is entirely unaffected by an AI-model outage, so
 * the message says so explicitly, and never leaks internal details (e.g. a
 * connection-refused stack trace) about the local model dependency — the
 * {@code cause} is retained only for server-side logging.
 */
public class InsightUnavailableException extends ApiException {

    private static final String MESSAGE =
            "The trend insight could not be generated. The rate data itself is still available "
                    + "via /api/exchange/history.";

    public InsightUnavailableException(Throwable cause) {
        super("INSIGHT_UNAVAILABLE", MESSAGE, cause);
    }
}
