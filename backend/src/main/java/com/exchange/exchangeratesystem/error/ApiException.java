package com.exchange.exchangeratesystem.error;

/**
 * Base for exceptions that map directly to a documented API error response
 * ({@code { "error": "<code>", "message": "..." }}, per contracts/*.md).
 * {@link GlobalExceptionHandler} maps each concrete subclass to a fixed HTTP
 * status; the exception itself only carries the machine-readable error code
 * and a human-readable message — no HTTP concerns.
 */
public abstract class ApiException extends RuntimeException {

    private final String errorCode;

    protected ApiException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected ApiException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
