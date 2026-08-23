package com.exchange.exchangeratesystem.error;

import java.time.LocalDate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every exception a controller can surface to the one {@code {error,
 * message}} body shape used across contracts/*.md, so no endpoint ever leaks
 * a raw stack trace or Spring's default error shape.
 *
 * Three fixed mappings per T021 (the three {@link ApiException} subclasses,
 * T020); the rest are framework-level parsing/binding failures that occur
 * before a controller ever gets to raise a domain exception (e.g. a malformed
 * {@code date} query parameter never reaches a currency-validation check —
 * Spring's own argument binding rejects it first), plus a catch-all so an
 * unanticipated exception still returns our shape instead of Spring Boot's
 * default error body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnknownCurrencyException.class)
    public ResponseEntity<ErrorResponse> handleUnknownCurrency(UnknownCurrencyException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(RateNotAvailableException.class)
    public ResponseEntity<ErrorResponse> handleRateNotAvailable(RateNotAvailableException e) {
        return errorResponse(HttpStatus.NOT_FOUND, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(UpstreamFetchException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamFetch(UpstreamFetchException e) {
        log.error("Upstream fetch failure surfaced to a client: {}", e.getMessage());
        return errorResponse(HttpStatus.BAD_GATEWAY, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDateRange(InvalidDateRangeException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(InsightUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleInsightUnavailable(InsightUnavailableException e) {
        log.error("Trend insight generation failed: {}", e.getCause() != null
                ? e.getCause().getMessage()
                : e.getMessage());
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, e.getErrorCode(), e.getMessage());
    }

    /**
     * A query parameter failed type conversion — most notably a malformed
     * {@code date}/{@code startDate}/{@code endDate}/{@code fromDate}/
     * {@code toDate}, which is contracts/exchange.md's and contracts/insight.md's
     * documented {@code INVALID_DATE_FORMAT} case.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String errorCode =
                LocalDate.class.equals(e.getRequiredType()) ? "INVALID_DATE_FORMAT" : "INVALID_PARAMETER";
        String message = "Invalid value for parameter '" + e.getName() + "': " + e.getValue();
        return errorResponse(HttpStatus.BAD_REQUEST, errorCode, message);
    }

    /** A required query parameter (e.g. {@code from}, {@code to}) was omitted entirely. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", e.getMessage());
    }

    /** {@code @Valid}-annotated request body failed bean validation. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message =
                e.getBindingResult().getFieldErrors().stream()
                        .map(err -> err.getField() + ": " + err.getDefaultMessage())
                        .collect(Collectors.joining("; "));
        return errorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /** The request body could not be parsed (malformed JSON, wrong shape, etc.). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return errorResponse(
                HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY", "The request body could not be read");
    }

    /** Anything not already mapped above — never let a raw exception reach the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception reached GlobalExceptionHandler", e);
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> errorResponse(
            HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(errorCode, message));
    }
}
