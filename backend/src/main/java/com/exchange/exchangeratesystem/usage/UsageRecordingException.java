package com.exchange.exchangeratesystem.usage;

/**
 * Raised when {@code UsageTrackingService} could not record a lookup after
 * exhausting its retry budget — a service-level failure, not the raw
 * {@code DataIntegrityViolationException} that caused it.
 */
public class UsageRecordingException extends RuntimeException {

    public UsageRecordingException(String message) {
        super(message);
    }
}
